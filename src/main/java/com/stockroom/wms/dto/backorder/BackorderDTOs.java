package com.stockroom.wms.dto.backorder;

import com.stockroom.wms.domain.BackorderStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public class BackorderDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BackorderResponse {
        private Long id;
        private String orderId;
        private Long productId;
        private String productSku;
        private String productName;
        private Integer quantityOrdered;
        private Integer quantityFulfilled;
        private Integer quantityPending;
        private BackorderStatus status;
        private Instant createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FulfillBackorderRequest {
        @NotNull(message = "Location ID to fulfill from is required")
        private Long locationId;

        @NotNull(message = "Quantity to fulfill is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;
    }
}
