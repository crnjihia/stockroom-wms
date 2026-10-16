package com.stockroom.wms.repository;

import com.stockroom.wms.domain.ReturnStatus;
import com.stockroom.wms.domain.StockReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockReturnRepository extends JpaRepository<StockReturn, Long> {
    List<StockReturn> findByStatus(ReturnStatus status);
    List<StockReturn> findByOriginalOrderId(String originalOrderId);
}
