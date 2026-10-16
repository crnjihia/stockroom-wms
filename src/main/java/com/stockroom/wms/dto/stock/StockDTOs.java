package com.stockroom.wms.dto.stock;

import com.stockroom.wms.domain.MovementType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

public class StockDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReceiveStockRequest {
        @NotNull(message = "Product ID is required")
        private Long productId;

        @NotNull(message = "Location ID is required")
        private Long locationId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        @NotBlank(message = "Batch number is required")
        private String batchNumber;

        private LocalDate expiryDate;

        private String reference;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class IssueStockRequest {
        @NotNull(message = "Product ID is required")
        private Long productId;

        @NotNull(message = "Location ID is required")
        private Long locationId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        private String reference;
        private String batchNumber;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransferStockRequest {
        @NotNull(message = "Source location ID is required")
        private Long fromLocationId;

        @NotNull(message = "Destination location ID is required")
        private Long toLocationId;

        @NotNull(message = "Product ID is required")
        private Long productId;

        @NotBlank(message = "Batch number is required")
        private String batchNumber;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        private String notes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AdjustStockRequest {
        @NotNull(message = "Product ID is required")
        private Long productId;

        @NotNull(message = "Location ID is required")
        private Long locationId;

        @NotBlank(message = "Batch number is required")
        private String batchNumber;

        @NotNull(message = "New quantity is required")
        @Min(value = 0, message = "New quantity cannot be negative")
        private Integer newQuantity;

        @NotBlank(message = "Reason is required for inventory adjustment")
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StockMovementResponse {
        private Long id;
        private Long productId;
        private String productSku;
        private String productName;
        private Long locationId;
        private String locationCode;
        private String warehouseCode;
        private MovementType type;
        private Integer quantity;
        private String referenceType;
        private String referenceId;
        private String userId;
        private String notes;
        private Instant createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReconciliationResult {
        private Long productId;
        private Long locationId;
        private int currentOnHand;
        private int calculatedMovementsSum;
        private boolean isBalanced;
        private String status;
    }
}
