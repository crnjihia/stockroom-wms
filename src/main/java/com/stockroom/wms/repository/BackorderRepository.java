package com.stockroom.wms.repository;

import com.stockroom.wms.domain.Backorder;
import com.stockroom.wms.domain.BackorderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BackorderRepository extends JpaRepository<Backorder, Long> {
    List<Backorder> findByStatus(BackorderStatus status);
    List<Backorder> findByProductId(Long productId);
    List<Backorder> findByOrderId(String orderId);
}
