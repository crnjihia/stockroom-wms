package com.stockroom.wms.scheduler;

import com.stockroom.wms.domain.StockItem;
import com.stockroom.wms.dto.stock.StockDTOs.ReconciliationResult;
import com.stockroom.wms.repository.StockItemRepository;
import com.stockroom.wms.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class StockReconciliationScheduler {

    private final StockItemRepository stockItemRepository;
    private final StockService stockService;

    public StockReconciliationScheduler(StockItemRepository stockItemRepository, StockService stockService) {
        this.stockItemRepository = stockItemRepository;
        this.stockService = stockService;
    }

    /**
     * Nightly reconciliation job running at 2:00 AM asserting ledger integrity:
     * quantityOnHand == SUM(movements) for every product + location.
     */
    @Scheduled(cron = "${app.scheduling.reconciliation-cron:0 0 2 * * *}")
    public void runNightlyReconciliation() {
        log.info("Starting Nightly Stock Ledger Reconciliation Job...");
        List<StockItem> items = stockItemRepository.findAll();
        Set<String> audited = new HashSet<>();
        int passed = 0;
        int discrepancies = 0;

        for (StockItem item : items) {
            String key = item.getProduct().getId() + "_" + item.getLocation().getId();
            if (!audited.add(key)) continue;

            try {
                ReconciliationResult result = stockService.reconcileLedger(
                        item.getProduct().getId(), item.getLocation().getId()
                );
                passed++;
            } catch (Exception ex) {
                discrepancies++;
                log.error("Reconciliation failure for Product {} at Location {}: {}",
                        item.getProduct().getSku(), item.getLocation().getCode(), ex.getMessage());
            }
        }

        log.info("Nightly Stock Ledger Reconciliation completed. Checked: {} pairings, Balanced: {}, Discrepancies: {}",
                audited.size(), passed, discrepancies);
    }
}
