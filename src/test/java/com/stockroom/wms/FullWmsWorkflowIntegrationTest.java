package com.stockroom.wms;

import com.stockroom.wms.domain.*;
import com.stockroom.wms.dto.reservation.ReservationDTOs.CreateReservationRequest;
import com.stockroom.wms.dto.reservation.ReservationDTOs.ReservationResponse;
import com.stockroom.wms.dto.returns.ReturnDTOs.*;
import com.stockroom.wms.dto.stock.StockDTOs.*;
import com.stockroom.wms.repository.*;
import com.stockroom.wms.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "admin", roles = {"ADMIN", "WAREHOUSE_MANAGER"})
public class FullWmsWorkflowIntegrationTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReturnService returnService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private StockReturnRepository returnRepository;

    @Autowired
    private BackorderRepository backorderRepository;

    private Product product;
    private Warehouse warehouse;
    private Location storageLoc;
    private Location returnsLoc;

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

        warehouse = warehouseRepository.save(Warehouse.builder()
                .code("NBO-FLOW")
                .name("Nairobi Integration Depot")
                .county("Nairobi")
                .town("Industrial Area")
                .address("Enterprise Road")
                .active(true)
                .build());

        storageLoc = locationRepository.save(Location.builder()
                .warehouse(warehouse)
                .code("STG-01")
                .zone(Zone.STORAGE)
                .capacityUnits(5000)
                .build());

        returnsLoc = locationRepository.save(Location.builder()
                .warehouse(warehouse)
                .code("RET-01")
                .zone(Zone.RETURNS)
                .capacityUnits(2000)
                .build());

        product = productRepository.save(Product.builder()
                .sku("RNA-OIL-5L")
                .name("Rina Vegetable Cooking Oil 5L")
                .category("Edible Oils")
                .unitOfMeasure("JERRICAN")
                .barcode("06161103456782")
                .reorderPoint(50)
                .reorderQty(200)
                .unitCost(BigDecimal.valueOf(1150.00))
                .unitPrice(BigDecimal.valueOf(1320.00))
                .build());
    }

    @Test
    @DisplayName("End-to-End Workflow: Receive -> Reserve -> Issue (Fulfill) -> Return -> Restock -> Invariant Check")
    void testEndToEndWmsLifecycle() {
        // Step 1: Receive Stock (50 jerricans)
        ReceiveStockRequest receiveReq = ReceiveStockRequest.builder()
                .productId(product.getId())
                .locationId(storageLoc.getId())
                .quantity(50)
                .batchNumber("BAT-OIL-2026")
                .expiryDate(LocalDate.of(2028, 1, 1))
                .reference("PO-KENYA-1001")
                .build();

        StockMovementResponse receiveResp = stockService.receiveStock(receiveReq, "admin");
        assertNotNull(receiveResp);
        assertEquals(MovementType.RECEIPT, receiveResp.getType());
        assertEquals(50, receiveResp.getQuantity());

        StockItem itemAfterReceive = stockItemRepository.findByProductIdAndLocationIdAndBatchNumber(
                product.getId(), storageLoc.getId(), "BAT-OIL-2026"
        ).orElseThrow();
        assertEquals(50, itemAfterReceive.getQuantityOnHand());
        assertEquals(0, itemAfterReceive.getQuantityReserved());
        assertEquals(50, itemAfterReceive.getQuantityAvailable());

        // Step 2: Reserve Stock (15 jerricans for Order ORD-99)
        CreateReservationRequest resReq = CreateReservationRequest.builder()
                .orderId("ORD-99")
                .productId(product.getId())
                .quantity(15)
                .ttlMinutes(60)
                .build();

        ReservationResponse resResp = reservationService.createReservation(resReq);
        assertNotNull(resResp);
        assertEquals(ReservationStatus.ACTIVE, resResp.getStatus());
        assertEquals(15, resResp.getQuantity());

        StockItem itemAfterReserve = stockItemRepository.findById(itemAfterReceive.getId()).orElseThrow();
        assertEquals(50, itemAfterReserve.getQuantityOnHand());
        assertEquals(15, itemAfterReserve.getQuantityReserved());
        assertEquals(35, itemAfterReserve.getQuantityAvailable());

        // Step 3: Fulfill Reservation (Picking & Dispatch of 15 jerricans)
        ReservationResponse fulfillResp = reservationService.fulfillReservation(resResp.getId(), "picker");
        assertEquals(ReservationStatus.FULFILLED, fulfillResp.getStatus());

        StockItem itemAfterFulfill = stockItemRepository.findById(itemAfterReceive.getId()).orElseThrow();
        assertEquals(35, itemAfterFulfill.getQuantityOnHand());
        assertEquals(0, itemAfterFulfill.getQuantityReserved());
        assertEquals(35, itemAfterFulfill.getQuantityAvailable());

        // Verify ledger integrity at storage location
        ReconciliationResult recon1 = stockService.reconcileLedger(product.getId(), storageLoc.getId());
        assertTrue(recon1.isBalanced());
        assertEquals(35, recon1.getCurrentOnHand());
        assertEquals(35, recon1.getCalculatedMovementsSum());

        // Step 4: Customer Return (Customer returns 5 units due to ordering wrong item)
        CreateReturnRequest returnReq = CreateReturnRequest.builder()
                .originalOrderId("ORD-99")
                .productId(product.getId())
                .quantity(5)
                .reason(ReturnReason.WRONG_ITEM)
                .build();

        ReturnResponse retResp = returnService.createReturn(returnReq);
        assertEquals(ReturnStatus.PENDING, retResp.getStatus());

        // Inspect return
        ReturnResponse inspected = returnService.inspectReturn(
                retResp.getId(),
                new InspectReturnRequest("Quality seals intact, factory packaging verified undamaged")
        );
        assertEquals(ReturnStatus.INSPECTED, inspected.getStatus());

        // Restock return into storage location
        ReturnResponse restocked = returnService.restockReturn(
                retResp.getId(),
                new RestockReturnRequest(storageLoc.getId(), "BAT-OIL-2026"),
                "auditor"
        );
        assertEquals(ReturnStatus.RESTOCKED, restocked.getStatus());

        // Check final stock item: 35 + 5 = 40 units on hand
        StockItem itemAfterRestock = stockItemRepository.findById(itemAfterReceive.getId()).orElseThrow();
        assertEquals(40, itemAfterRestock.getQuantityOnHand());
        assertEquals(40, itemAfterRestock.getQuantityAvailable());

        // Step 5: Verify Final Stock Ledger Invariant: quantityOnHand == SUM(movements)
        // Movements: +50 (RECEIPT) -15 (ISSUE) +5 (RETURN) = 40
        ReconciliationResult finalRecon = stockService.reconcileLedger(product.getId(), storageLoc.getId());
        assertTrue(finalRecon.isBalanced());
        assertEquals(40, finalRecon.getCurrentOnHand());
        assertEquals(40, finalRecon.getCalculatedMovementsSum());
    }
}
