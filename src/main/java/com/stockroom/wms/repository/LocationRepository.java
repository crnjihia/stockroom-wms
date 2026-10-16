package com.stockroom.wms.repository;

import com.stockroom.wms.domain.Location;
import com.stockroom.wms.domain.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findByWarehouseId(Long warehouseId);
    Optional<Location> findByWarehouseIdAndCode(Long warehouseId, String code);
    Optional<Location> findFirstByWarehouseIdAndZone(Long warehouseId, Zone zone);
    boolean existsByWarehouseIdAndCode(Long warehouseId, String code);
}
