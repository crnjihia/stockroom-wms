package com.stockroom.wms.service;

import com.stockroom.wms.domain.Location;
import com.stockroom.wms.domain.Warehouse;
import com.stockroom.wms.dto.warehouse.WarehouseDTOs.*;
import com.stockroom.wms.exception.InvalidOperationException;
import com.stockroom.wms.exception.ResourceNotFoundException;
import com.stockroom.wms.repository.LocationRepository;
import com.stockroom.wms.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final LocationRepository locationRepository;

    public WarehouseService(WarehouseRepository warehouseRepository, LocationRepository locationRepository) {
        this.warehouseRepository = warehouseRepository;
        this.locationRepository = locationRepository;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public WarehouseResponse createWarehouse(WarehouseRequest request) {
        if (warehouseRepository.existsByCode(request.getCode())) {
            throw new InvalidOperationException("Warehouse code '" + request.getCode() + "' already exists");
        }

        Warehouse warehouse = Warehouse.builder()
                .code(request.getCode().toUpperCase().trim())
                .name(request.getName().trim())
                .county(request.getCounty().trim())
                .town(request.getTown().trim())
                .address(request.getAddress().trim())
                .managerId(request.getManagerId())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        Warehouse saved = warehouseRepository.save(warehouse);
        log.info("Created warehouse '{}' with ID {}", saved.getCode(), saved.getId());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> getAllWarehouses() {
        return warehouseRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WarehouseResponse getWarehouseById(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + id));
        return mapToResponse(warehouse);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER')")
    @Transactional
    public LocationResponse createLocation(Long warehouseId, LocationRequest request) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + warehouseId));

        String code = request.getCode().toUpperCase().trim();
        if (locationRepository.existsByWarehouseIdAndCode(warehouseId, code)) {
            throw new InvalidOperationException("Location '" + code + "' already exists in warehouse " + warehouse.getCode());
        }

        Location location = Location.builder()
                .warehouse(warehouse)
                .code(code)
                .zone(request.getZone())
                .capacityUnits(request.getCapacityUnits() != null ? request.getCapacityUnits() : 1000)
                .build();

        Location saved = locationRepository.save(location);
        log.info("Created location '{}' in warehouse '{}'", saved.getCode(), warehouse.getCode());

        return LocationResponse.builder()
                .id(saved.getId())
                .warehouseId(warehouse.getId())
                .warehouseCode(warehouse.getCode())
                .code(saved.getCode())
                .zone(saved.getZone())
                .capacityUnits(saved.getCapacityUnits())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> getLocationsByWarehouse(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + warehouseId));

        return locationRepository.findByWarehouseId(warehouseId).stream()
                .map(loc -> LocationResponse.builder()
                        .id(loc.getId())
                        .warehouseId(warehouse.getId())
                        .warehouseCode(warehouse.getCode())
                        .code(loc.getCode())
                        .zone(loc.getZone())
                        .capacityUnits(loc.getCapacityUnits())
                        .createdAt(loc.getCreatedAt())
                        .build())
                .toList();
    }

    private WarehouseResponse mapToResponse(Warehouse w) {
        return WarehouseResponse.builder()
                .id(w.getId())
                .code(w.getCode())
                .name(w.getName())
                .county(w.getCounty())
                .town(w.getTown())
                .address(w.getAddress())
                .managerId(w.getManagerId())
                .active(w.getActive())
                .locationsCount(w.getLocations() != null ? w.getLocations().size() : 0)
                .createdAt(w.getCreatedAt())
                .build();
    }
}
