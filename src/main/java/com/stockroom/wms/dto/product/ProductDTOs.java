package com.stockroom.wms.dto.product;

import com.stockroom.wms.domain.Zone;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ProductDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductRequest {
        @NotBlank(message = "SKU is required")
        private String sku;

        @NotBlank(message = "Product name is required")
        private String name;

        private String description;

        @NotBlank(message = "Category is required")
        private String category;

        @NotBlank(message = "Unit of measure is required (e.g., BALE, CARTON, JERRICAN)")
        private String unitOfMeasure;

        @NotBlank(message = "Barcode (EAN-13/GS1-128) is required")
        private String barcode;

        @NotNull(message = "Reorder point is required")
        @Min(0)
        private Integer reorderPoint;

        @NotNull(message = "Reorder quantity is required")
        @Min(1)
        private Integer reorderQty;

        @NotNull(message = "Unit cost is required")
        @DecimalMin("0.0")
        private BigDecimal unitCost;

        @NotNull(message = "Unit price is required")
        @DecimalMin("0.0")
        private BigDecimal unitPrice;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductResponse {
        private Long id;
        private String sku;
        private String name;
        private String description;
        private String category;
        private String unitOfMeasure;
        private String barcode;
        private Integer reorderPoint;
        private Integer reorderQty;
        private BigDecimal unitCost;
        private BigDecimal unitPrice;
        private Instant createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductStockSummaryResponse {
        private Long productId;
        private String sku;
        private String name;
        private int totalOnHand;
        private int totalReserved;
        private int totalAvailable;
        @Builder.Default
        private List<WarehouseStockBreakdown> warehouses = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WarehouseStockBreakdown {
        private Long warehouseId;
        private String warehouseCode;
        private String warehouseName;
        private int onHand;
        private int reserved;
        private int available;
        @Builder.Default
        private List<LocationStockDetail> items = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LocationStockDetail {
        private Long stockItemId;
        private Long locationId;
        private String locationCode;
        private Zone zone;
        private String batchNumber;
        private LocalDate expiryDate;
        private int quantityOnHand;
        private int quantityReserved;
        private int quantityAvailable;
    }
}
