package com.stockroom.wms;

import com.stockroom.wms.domain.*;
import com.stockroom.wms.dto.reservation.ReservationDTOs.CreateReservationRequest;
import com.stockroom.wms.dto.reservation.ReservationDTOs.ReservationResponse;
import com.stockroom.wms.exception.InsufficientStockException;
import com.stockroom.wms.exception.OptimisticLockRetryExhaustedException;
import com.stockroom.wms.repository.*;
import com.stockroom.wms.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ReservationConcurrencyTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private StockReturnRepository returnRepository;

    @Autowired
    private BackorderRepository backorderRepository;

    private Product testProduct;
    private Location testLocation;
    private Warehouse testWarehouse;

    @BeforeEach
    void setUp() {
        returnRepository.deleteAll();
        reservationRepository.deleteAll();
        backorderRepository.deleteAll();
        stockMovementRepository.deleteAll();
        stockItemRepository.deleteAll();
        locationRepository.deleteAll();
        warehouseRepository.deleteAll();
        productRepository.deleteAll();

        testWarehouse = warehouseRepository.save(Warehouse.builder()
                .code("NBO-TEST")
                .name("Nairobi Test Depot")
                .county("Nairobi")
                .town("Industrial Area")
                .address("Enterprise Road")
                .active(true)
                .build());

        testLocation = locationRepository.save(Location.builder()
                .warehouse(testWarehouse)
                .code("BIN-TEST-01")
                .zone(Zone.STORAGE)
                .capacityUnits(5000)
                .build());

        testProduct = productRepository.save(Product.builder()
                .sku("PMB-MZ-TEST")
                .name("Pembe Maize Flour 2kg Test")
                .category("Dry Foods")
                .unitOfMeasure("BALE")
                .barcode("06161101234566")
                .reorderPoint(20)
                .reorderQty(50)
                .unitCost(BigDecimal.valueOf(200.00))
                .unitPrice(BigDecimal.valueOf(240.00))
                .build());
    }

    @Test
    @DisplayName("Concurrent Reservations: 100 threads competing for 10 units -> exactly 10 succeed, 0 oversell")
    void testConcurrentReservationsOversellPrevention() throws InterruptedException {
        // Seed exactly 10 units on hand
        StockItem stockItem = stockItemRepository.save(StockItem.builder()
                .product(testProduct)
                .location(testLocation)
                .quantityOnHand(10)
                .quantityReserved(0)
                .batchNumber("BAT-CONCURRENCY-10")
                .expiryDate(LocalDate.of(2027, 12, 31))
                .version(0L)
                .build());

        int numberOfThreads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Simultaneous burst
                    CreateReservationRequest request = CreateReservationRequest.builder()
                            .orderId("ORD-CONCUR-" + index)
                            .productId(testProduct.getId())
                            .quantity(1)
                            .ttlMinutes(30)
                            .build();

                    ReservationResponse resp = null;
                    int clientAttempts = 0;
                    while (resp == null && clientAttempts < 10) {
                        clientAttempts++;
                        try {
                            resp = reservationService.createReservation(request);
                        } catch (OptimisticLockRetryExhaustedException e) {
                            Thread.sleep((long) (20 + (Math.random() * 30)));
                        } catch (InsufficientStockException e) {
                            break; // Stock is depleted!
                        }
                    }

                    if (resp != null && resp.getId() != null) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown(); // Release all 100 threads
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "All threads must finish within timeout");

        // Verify invariant: exactly 10 successful reservations
        assertEquals(10, successCount.get(), "Exactly 10 threads must succeed in reserving the 10 units");
        assertEquals(90, failureCount.get(), "Remaining 90 threads must fail due to stock depletion");

        // Check database state
        StockItem refreshedItem = stockItemRepository.findById(stockItem.getId()).orElseThrow();
        assertEquals(10, refreshedItem.getQuantityOnHand());
        assertEquals(10, refreshedItem.getQuantityReserved());
        assertEquals(0, refreshedItem.getQuantityAvailable(), "No available stock should remain");
    }
}
