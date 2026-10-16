package com.stockroom.wms.repository;

import com.stockroom.wms.domain.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    Optional<StockItem> findByProductIdAndLocationIdAndBatchNumber(Long productId, Long locationId, String batchNumber);

    List<StockItem> findByProductId(Long productId);

    List<StockItem> findByProductIdAndLocationWarehouseId(Long productId, Long warehouseId);

    List<StockItem> findByLocationWarehouseId(Long warehouseId);

    List<StockItem> findByLocationId(Long locationId);

    @Query("SELECT COALESCE(SUM(s.quantityOnHand), 0) FROM StockItem s WHERE s.product.id = :productId")
    int sumOnHandByProductId(@Param("productId") Long productId);

    @Query("SELECT COALESCE(SUM(s.quantityOnHand), 0) FROM StockItem s WHERE s.product.id = :productId AND s.location.warehouse.id = :warehouseId")
    int sumOnHandByProductIdAndWarehouseId(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId);

    @Query("SELECT COALESCE(SUM(s.quantityReserved), 0) FROM StockItem s WHERE s.product.id = :productId")
    int sumReservedByProductId(@Param("productId") Long productId);

    @Query("SELECT COALESCE(SUM(s.quantityReserved), 0) FROM StockItem s WHERE s.product.id = :productId AND s.location.warehouse.id = :warehouseId")
    int sumReservedByProductIdAndWarehouseId(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId);

    @Query("SELECT s FROM StockItem s WHERE (s.quantityOnHand - s.quantityReserved) > 0 AND s.product.id = :productId ORDER BY s.expiryDate ASC, s.id ASC")
    List<StockItem> findAvailableStockFifo(@Param("productId") Long productId);

    @Query("SELECT s FROM StockItem s WHERE (s.quantityOnHand - s.quantityReserved) > 0 AND s.product.id = :productId AND s.location.warehouse.id = :warehouseId ORDER BY s.expiryDate ASC, s.id ASC")
    List<StockItem> findAvailableStockFifoByWarehouse(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId);
}
