package com.stockroom.wms.repository;

import com.stockroom.wms.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySku(String sku);
    Optional<Product> findByBarcode(String barcode);
    boolean existsBySku(String sku);

    @Query("SELECT p FROM Product p WHERE " +
           "(cast(:query as string) IS NULL OR LOWER(p.sku) LIKE LOWER(CONCAT('%', cast(:query as string), '%')) OR LOWER(p.name) LIKE LOWER(CONCAT('%', cast(:query as string), '%'))) AND " +
           "(cast(:category as string) IS NULL OR LOWER(p.category) = LOWER(cast(:category as string)))")
    Page<Product> searchProducts(@Param("query") String query, @Param("category") String category, Pageable pageable);
}
