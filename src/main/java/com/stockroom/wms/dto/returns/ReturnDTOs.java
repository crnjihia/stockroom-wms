package com.stockroom.wms.dto.returns;

import com.stockroom.wms.domain.ReturnReason;
import com.stockroom.wms.domain.ReturnStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public class ReturnDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateReturnRequest {
        @NotBlank(message = "Original order ID is required")
        private String originalOrderId;

        @NotNull(message = "Product ID is required")
        private Long productId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        @NotNull(message = "Return reason is required (DAMAGED/WRONG_ITEM/EXPIRED/CUSTOMER_RETURN)")
        private ReturnReason reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InspectReturnRequest {
        @NotBlank(message = "Inspection notes are required")
        private String inspectionNotes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RestockReturnRequest {
        @NotNull(message = "Target location ID is required for restocking")
        private Long targetLocationId;

        @NotBlank(message = "Batch number is required")
        private String batchNumber;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReturnResponse {
        private Long id;
        private String originalOrderId;
        private Long productId;
        private String productSku;
        private String productName;
        private Integer quantity;
        private ReturnReason reason;
        private ReturnStatus status;
        private String inspectionNotes;
        private Instant createdAt;
    }
}
