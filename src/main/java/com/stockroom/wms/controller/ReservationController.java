package com.stockroom.wms.controller;

import com.stockroom.wms.dto.reservation.ReservationDTOs.*;
import com.stockroom.wms.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reservations")
@Tag(name = "Reservations", description = "Endpoints for optimistic-locking stock reservations and fulfillment")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Operation(summary = "Create a stock reservation with optimistic lock concurrency control")
    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(@Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.createReservation(request));
    }

    @Operation(summary = "Fulfill an active reservation upon order picking and dispatch")
    @PostMapping("/{id}/fulfill")
    public ResponseEntity<ReservationResponse> fulfillReservation(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String userId = authentication != null ? authentication.getName() : "system";
        return ResponseEntity.ok(reservationService.fulfillReservation(id, userId));
    }

    @Operation(summary = "Release an active reservation back to available inventory")
    @PostMapping("/{id}/release")
    public ResponseEntity<ReservationResponse> releaseReservation(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String userId = authentication != null ? authentication.getName() : "system";
        return ResponseEntity.ok(reservationService.releaseReservation(id, userId));
    }
}
