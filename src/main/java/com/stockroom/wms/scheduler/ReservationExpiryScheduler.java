package com.stockroom.wms.scheduler;

import com.stockroom.wms.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ReservationExpiryScheduler {

    private final ReservationService reservationService;

    public ReservationExpiryScheduler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * Periodically releases expired reservations back to available stock.
     */
    @Scheduled(fixedDelayString = "${app.scheduling.reservation-expiry-ms:60000}")
    public void runReservationExpiryCleanup() {
        int expiredCount = reservationService.expireStaleReservations();
        if (expiredCount > 0) {
            log.info("ReservationExpiryScheduler: Released {} expired stock reservations", expiredCount);
        }
    }
}
