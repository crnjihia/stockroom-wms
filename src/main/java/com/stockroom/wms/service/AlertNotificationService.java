package com.stockroom.wms.service;

import com.stockroom.wms.domain.ReorderAlert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AlertNotificationService {

    public void sendLowStockEmail(ReorderAlert alert) {
        log.info("[NOTIFICATION - EMAIL DISPATCH] To: procurement@stockroom.co.ke | Subject: CRITICAL: Low Stock Reorder Alert for {} (SKU: {}) | Current: {}, Reorder Point: {}, Suggested Order: {} units at Depot {}",
                alert.getProduct().getName(),
                alert.getProduct().getSku(),
                alert.getCurrentQty(),
                alert.getReorderPoint(),
                alert.getSuggestedQty(),
                alert.getWarehouse().getCode());
    }

    public void sendInAppNotification(ReorderAlert alert) {
        log.info("[NOTIFICATION - IN-APP ALERT] Depot Manager Notification created for Warehouse {}: Low stock on SKU {} ({}) - Available: {} units. Immediate PO recommended.",
                alert.getWarehouse().getCode(),
                alert.getProduct().getSku(),
                alert.getProduct().getName(),
                alert.getCurrentQty());
    }
}
