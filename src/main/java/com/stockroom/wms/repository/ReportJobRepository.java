package com.stockroom.wms.repository;

import com.stockroom.wms.domain.ReportJob;
import com.stockroom.wms.domain.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportJobRepository extends JpaRepository<ReportJob, String> {
    List<ReportJob> findByStatus(ReportStatus status);
}
