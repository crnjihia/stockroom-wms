package com.stockroom.wms.dto.barcode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

public class BarcodeDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BarcodeScanRequest {
        @NotBlank(message = "Barcode string is required")
        private String barcode;

        @NotBlank(message = "Action is required ('IN' or 'OUT')")
        @Pattern(regexp = "^(IN|OUT)$", message = "Action must be either 'IN' or 'OUT'")
        private String action;

        @NotNull(message = "Location ID is required")
        private Long locationId;

        @Builder.Default
        private Integer quantity = 1;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BarcodeScanResponse {
        private String action;
        private String gtin;
        private String sku;
        private String productName;
        private String batchNumber;
        private LocalDate expiryDate;
        private String serialNumber;
        private Long locationId;
        private String locationCode;
        private int quantityMoved;
        private String status;
        private String message;
        private Long movementId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParsedGs1Data {
        private String gtin;
        private String batchNumber;
        private LocalDate expiryDate;
        private String serialNumber;
        private String rawBarcode;
        private boolean checkDigitValid;
    }
}
