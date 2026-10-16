package com.stockroom.wms.dto.warehouse;

import com.stockroom.wms.domain.Zone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public class WarehouseDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WarehouseRequest {
        @NotBlank(message = "Warehouse code is required")
        @Pattern(regexp = "^[A-Z0-9_-]{3,20}$", message = "Code must be uppercase alphanumeric (e.g., NBO-01)")
        private String code;

        @NotBlank(message = "Warehouse name is required")
        private String name;

        @NotBlank(message = "County is required")
        private String county;

        @NotBlank(message = "Town is required")
        private String town;

        @NotBlank(message = "Address is required")
        private String address;

        private Long managerId;

        @Builder.Default
        private Boolean active = true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WarehouseResponse {
        private Long id;
        private String code;
        private String name;
        private String county;
        private String town;
        private String address;
        private Long managerId;
        private Boolean active;
        private int locationsCount;
        private Instant createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LocationRequest {
        @NotBlank(message = "Location code is required (e.g., A-01-03)")
        private String code;

        @NotNull(message = "Zone is required (RECEIVING/STORAGE/PICKING/SHIPPING/RETURNS)")
        private Zone zone;

        @Builder.Default
        private Integer capacityUnits = 1000;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LocationResponse {
        private Long id;
        private Long warehouseId;
        private String warehouseCode;
        private String code;
        private Zone zone;
        private Integer capacityUnits;
        private Instant createdAt;
    }
}
