package com.stockroom.wms.scheduler;

import com.stockroom.wms.domain.AlertStatus;
import com.stockroom.wms.domain.Product;
import com.stockroom.wms.domain.ReorderAlert;
import com.stockroom.wms.domain.Warehouse;
import com.stockroom.wms.repository.ProductRepository;
import com.stockroom.wms.repository.ReorderAlertRepository;
import com.stockroom.wms.repository.StockItemRepository;
import com.stockroom.wms.repository.WarehouseRepository;
import com.stockroom.wms.service.AlertNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class LowStockReorderScheduler {

    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockItemRepository stockItemRepository;
    private final ReorderAlertRepository reorderAlertRepository;
    private final AlertNotificationService notificationService;

    public LowStockReorderScheduler(
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository,
            StockItemRepository stockItemRepository,
            ReorderAlertRepository reorderAlertRepository,
            AlertNotificationService notificationService
    ) {
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockItemRepository = stockItemRepository;
        this.reorderAlertRepository = reorderAlertRepository;
        this.notificationService = notificationService;
    }

    /**
     * Runs every 6 hours to check inventory levels against reorder thresholds.
     */
    @Scheduled(cron = "${app.scheduling.low-stock-cron:0 0 */6 * * *}")
    @Transactional
    public void scanAndGenerateReorderAlerts() {
        log.info("Executing Low-Stock Reorder Threshold Scanner...");
        List<Product> products = productRepository.findAll();
        List<Warehouse> warehouses = warehouseRepository.findAll();

        int alertsCreated = 0;

        for (Warehouse warehouse : warehouses) {
            for (Product product : products) {
                int onHand = stockItemRepository.sumOnHandByProductIdAndWarehouseId(product.getId(), warehouse.getId());

                if (onHand <= product.getReorderPoint()) {
                    boolean alreadyAlerted = reorderAlertRepository.existsByProductIdAndWarehouseIdAndStatusIn(
                            product.getId(),
                            warehouse.getId(),
                            Set.of(AlertStatus.OPEN, AlertStatus.ACKNOWLEDGED, AlertStatus.ORDERED)
                    );

                    if (!alreadyAlerted) {
                        ReorderAlert alert = ReorderAlert.builder()
                                .product(product)
                                .warehouse(warehouse)
                                .currentQty(onHand)
                                .reorderPoint(product.getReorderPoint())
                                .suggestedQty(product.getReorderQty())
                                .status(AlertStatus.OPEN)
                                .build();

                        ReorderAlert saved = reorderAlertRepository.save(alert);
                        alertsCreated++;

                        // Dispatch notifications
                        notificationService.sendLowStockEmail(saved);
                        notificationService.sendInAppNotification(saved);

                        log.warn("Created Reorder Alert #{} for SKU {} at Warehouse {}. On-hand: {}, Reorder Point: {}",
                                saved.getId(), product.getSku(), warehouse.getCode(), onHand, product.getReorderPoint());
                    }
                }
            }
        }

        log.info("Low-Stock Reorder scan completed. New alerts created: {}", alertsCreated);
    }
}
