package com.stockroom.wms.repository;

import com.stockroom.wms.domain.AlertStatus;
import com.stockroom.wms.domain.ReorderAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReorderAlertRepository extends JpaRepository<ReorderAlert, Long> {
    List<ReorderAlert> findByStatus(AlertStatus status);
    List<ReorderAlert> findByWarehouseId(Long warehouseId);
    boolean existsByProductIdAndWarehouseIdAndStatusIn(Long productId, Long warehouseId, Collection<AlertStatus> statuses);
    Optional<ReorderAlert> findByProductIdAndWarehouseIdAndStatus(Long productId, Long warehouseId, AlertStatus status);

    @Query("SELECT COUNT(a) FROM ReorderAlert a WHERE a.status = 'OPEN'")
    long countOpenAlerts();
}
