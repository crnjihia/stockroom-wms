package com.stockroom.wms.repository;

import com.stockroom.wms.domain.Reservation;
import com.stockroom.wms.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByOrderId(String orderId);
    List<Reservation> findByStatus(ReservationStatus status);

    @Query("SELECT r FROM Reservation r WHERE r.status = :status AND r.expiresAt <= :now")
    List<Reservation> findExpiredReservations(@Param("status") ReservationStatus status, @Param("now") Instant now);
}
