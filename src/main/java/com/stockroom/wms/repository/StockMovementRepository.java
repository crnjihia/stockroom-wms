package com.stockroom.wms.repository;

import com.stockroom.wms.domain.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {

    @Query("SELECT m FROM StockMovement m WHERE " +
           "(:productId IS NULL OR m.product.id = :productId) AND " +
           "(:locationId IS NULL OR m.location.id = :locationId) AND " +
           "(:fromTime IS NULL OR m.createdAt >= :fromTime) AND " +
           "(:toTime IS NULL OR m.createdAt <= :toTime) " +
           "ORDER BY m.createdAt DESC")
    List<StockMovement> findMovements(
            @Param("productId") Long productId,
            @Param("locationId") Long locationId,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime
    );

    @Query("SELECT COALESCE(SUM(" +
           "CASE " +
           " WHEN m.type = com.stockroom.wms.domain.MovementType.RECEIPT THEN m.quantity " +
           " WHEN m.type = com.stockroom.wms.domain.MovementType.RETURN THEN m.quantity " +
           " WHEN m.type = com.stockroom.wms.domain.MovementType.ISSUE THEN -m.quantity " +
           " WHEN m.type = com.stockroom.wms.domain.MovementType.ADJUSTMENT THEN m.quantity " +
           " WHEN m.type = com.stockroom.wms.domain.MovementType.TRANSFER THEN m.quantity " +
           " ELSE 0 END" +
           "), 0) FROM StockMovement m WHERE m.product.id = :productId AND m.location.id = :locationId")
    int calculateNetOnHandMovements(@Param("productId") Long productId, @Param("locationId") Long locationId);
}
