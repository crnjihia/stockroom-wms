package com.stockroom.wms.dto.reservation;

import com.stockroom.wms.domain.ReservationStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public class ReservationDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateReservationRequest {
        @NotBlank(message = "Order ID is required")
        private String orderId;

        @NotNull(message = "Product ID is required")
        private Long productId;

        private Long warehouseId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        @Builder.Default
        @Min(value = 1, message = "TTL must be at least 1 minute")
        private Integer ttlMinutes = 60;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReservationResponse {
        private Long id;
        private String orderId;
        private Long productId;
        private String productSku;
        private String productName;
        private Long locationId;
        private String locationCode;
        private String warehouseCode;
        private Integer quantity;
        private ReservationStatus status;
        private Instant expiresAt;
        private Instant createdAt;
    }
}
