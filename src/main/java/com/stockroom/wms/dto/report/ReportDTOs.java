package com.stockroom.wms.dto.report;

import com.stockroom.wms.domain.ReportStatus;
import com.stockroom.wms.domain.ReportType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class ReportDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AsyncReportRequest {
        private ReportType reportType;
        private Long warehouseId;
        private Instant from;
        private Instant to;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReportJobResponse {
        private String jobId;
        private ReportType reportType;
        private String format;
        private ReportStatus status;
        private String downloadUrl;
        private String errorMessage;
        private Long fileSizeBytes;
        private Instant createdAt;
        private Instant completedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InventoryReportRow {
        private String warehouseCode;
        private String warehouseName;
        private String locationCode;
        private String zone;
        private String sku;
        private String productName;
        private String category;
        private String batchNumber;
        private LocalDate expiryDate;
        private int quantityOnHand;
        private int quantityReserved;
        private int quantityAvailable;
        private BigDecimal unitCost;
        private BigDecimal totalValue;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ValuationReportRow {
        private String warehouseCode;
        private String sku;
        private String productName;
        private String batchNumber;
        private LocalDate expiryDate;
        private int quantity;
        private BigDecimal unitCost;
        private BigDecimal totalValuation;
        private String valuationMethod;
    }
}
