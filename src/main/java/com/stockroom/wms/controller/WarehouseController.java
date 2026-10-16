package com.stockroom.wms.controller;

import com.stockroom.wms.dto.warehouse.WarehouseDTOs.*;
import com.stockroom.wms.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/warehouses")
@Tag(name = "Warehouses & Locations", description = "Endpoints for managing depots and storage bins")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @Operation(summary = "Create a new warehouse depot (ADMIN only)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<WarehouseResponse> createWarehouse(@Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(warehouseService.createWarehouse(request));
    }

    @Operation(summary = "Get list of all warehouses")
    @GetMapping
    public ResponseEntity<List<WarehouseResponse>> getAllWarehouses() {
        return ResponseEntity.ok(warehouseService.getAllWarehouses());
    }

    @Operation(summary = "Get warehouse by ID")
    @GetMapping("/{id}")
    public ResponseEntity<WarehouseResponse> getWarehouseById(@PathVariable Long id) {
        return ResponseEntity.ok(warehouseService.getWarehouseById(id));
    }

    @Operation(summary = "Create a new location bin in warehouse")
    @PostMapping("/{id}/locations")
    public ResponseEntity<LocationResponse> createLocation(
            @PathVariable Long id,
            @Valid @RequestBody LocationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(warehouseService.createLocation(id, request));
    }

    @Operation(summary = "Get all locations for a specific warehouse")
    @GetMapping("/{id}/locations")
    public ResponseEntity<List<LocationResponse>> getLocations(@PathVariable Long id) {
        return ResponseEntity.ok(warehouseService.getLocationsByWarehouse(id));
    }
}
