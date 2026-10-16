package com.stockroom.wms.service;

import com.stockroom.wms.config.WmsMetrics;
import com.stockroom.wms.domain.*;
import com.stockroom.wms.dto.reservation.ReservationDTOs.*;
import com.stockroom.wms.exception.InsufficientStockException;
import com.stockroom.wms.exception.InvalidOperationException;
import com.stockroom.wms.exception.OptimisticLockRetryExhaustedException;
import com.stockroom.wms.exception.ResourceNotFoundException;
import com.stockroom.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Slf4j
public class ReservationService {

    private final ProductRepository productRepository;
    private final StockItemRepository stockItemRepository;
    private final ReservationRepository reservationRepository;
    private final StockMovementRepository stockMovementRepository;
    private final BackorderRepository backorderRepository;
    private final WmsMetrics wmsMetrics;

    public ReservationService(ProductRepository productRepository,
                              StockItemRepository stockItemRepository,
                              ReservationRepository reservationRepository,
                              StockMovementRepository stockMovementRepository,
                              BackorderRepository backorderRepository,
                              WmsMetrics wmsMetrics) {
        this.productRepository = productRepository;
        this.stockItemRepository = stockItemRepository;
        this.reservationRepository = reservationRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.backorderRepository = backorderRepository;
        this.wmsMetrics = wmsMetrics;
    }

    private static final int MAX_RETRY_ATTEMPTS = 5;

    /**
     * Executes reservation with retries on OptimisticLockingFailureException.
     */
    public ReservationResponse createReservation(CreateReservationRequest request) {
        int attempt = 0;
        while (attempt < MAX_RETRY_ATTEMPTS) {
            attempt++;
            try {
                return executeReservationAttempt(request);
            } catch (OptimisticLockingFailureException ex) {
                log.warn("OptimisticLockException during reservation attempt {} for order {}. Retrying...",
                        attempt, request.getOrderId());
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    wmsMetrics.recordReservationFailure("OPTIMISTIC_LOCK_COLLISION");
                    throw new OptimisticLockRetryExhaustedException(
                            "Failed to reserve stock after " + MAX_RETRY_ATTEMPTS + " attempts due to concurrent updates. Please retry.",
                            ex
                    );
                }
                try {
                    // Exponential backoff with randomized jitter to disperse concurrency collisions
                    long backoff = (30L * (1 << attempt)) + (long) (Math.random() * 50);
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Reservation interrupted", ie);
                }
            } catch (InsufficientStockException ex) {
                wmsMetrics.recordReservationFailure("INSUFFICIENT_STOCK");
                throw ex;
            }
        }
        throw new OptimisticLockRetryExhaustedException("Exhausted retries");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservationResponse executeReservationAttempt(CreateReservationRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));

        List<StockItem> availableItems;
        if (request.getWarehouseId() != null) {
            availableItems = stockItemRepository.findAvailableStockFifoByWarehouse(request.getProductId(), request.getWarehouseId());
        } else {
            availableItems = stockItemRepository.findAvailableStockFifo(request.getProductId());
        }

        int totalAvailable = availableItems.stream().mapToInt(StockItem::getQuantityAvailable).sum();
        if (totalAvailable < request.getQuantity()) {
            // Automatically log backorder for pending fulfillment
            recordBackorder(request.getOrderId(), product, request.getQuantity(), totalAvailable);
            throw new InsufficientStockException(String.format(
                    "Insufficient available stock to reserve for SKU %s. Requested: %d, Available: %d. A backorder has been registered.",
                    product.getSku(), request.getQuantity(), totalAvailable
            ));
        }

        // Allocate reservation to first available item (or split across FIFO items)
        StockItem targetItem = availableItems.get(0);
        for (StockItem item : availableItems) {
            if (item.getQuantityAvailable() >= request.getQuantity()) {
                targetItem = item;
                break;
            }
        }

        // Increment quantity reserved
        targetItem.setQuantityReserved(targetItem.getQuantityReserved() + request.getQuantity());
        // Save triggers JPA version check on targetItem
        StockItem savedItem = stockItemRepository.saveAndFlush(targetItem);

        int ttl = request.getTtlMinutes() != null ? request.getTtlMinutes() : 60;
        Instant expiresAt = Instant.now().plus(ttl, ChronoUnit.MINUTES);

        Reservation reservation = Reservation.builder()
                .orderId(request.getOrderId())
                .product(product)
                .location(savedItem.getLocation())
                .quantity(request.getQuantity())
                .status(ReservationStatus.ACTIVE)
                .expiresAt(expiresAt)
                .build();

        Reservation savedReservation = reservationRepository.save(reservation);

        // Record reservation movement in ledger for audit
        StockMovement movement = StockMovement.builder()
                .product(product)
                .location(savedItem.getLocation())
                .type(MovementType.RESERVATION)
                .quantity(0) // On-hand not altered yet, reserved allocated
                .referenceType("ORDER_RESERVATION")
                .referenceId(request.getOrderId())
                .notes("Reserved " + request.getQuantity() + " units for order " + request.getOrderId() + " at location " + savedItem.getLocation().getCode())
                .build();
        stockMovementRepository.save(movement);

        log.info("Successfully reserved {} units of SKU {} for order {}, reservation ID: {}, expires: {}",
                request.getQuantity(), product.getSku(), request.getOrderId(), savedReservation.getId(), expiresAt);

        return mapToResponse(savedReservation);
    }

    @Transactional
    public ReservationResponse fulfillReservation(Long reservationId, String userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new InvalidOperationException("Cannot fulfill reservation in status: " + reservation.getStatus());
        }

        StockItem item = stockItemRepository.findByProductIdAndLocationIdAndBatchNumber(
                reservation.getProduct().getId(),
                reservation.getLocation().getId(),
                // Find matching item in that location
                null
        ).orElseGet(() -> stockItemRepository.findByLocationId(reservation.getLocation().getId()).stream()
                .filter(i -> i.getProduct().getId().equals(reservation.getProduct().getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Stock item not found for reservation at location")));

        // Deduct both onHand and reserved
        item.setQuantityOnHand(Math.max(0, item.getQuantityOnHand() - reservation.getQuantity()));
        item.setQuantityReserved(Math.max(0, item.getQuantityReserved() - reservation.getQuantity()));
        stockItemRepository.save(item);

        reservation.setStatus(ReservationStatus.FULFILLED);
        Reservation updated = reservationRepository.save(reservation);

        // Record ISSUE movement in immutable ledger
        StockMovement movement = StockMovement.builder()
                .product(reservation.getProduct())
                .location(reservation.getLocation())
                .type(MovementType.ISSUE)
                .quantity(reservation.getQuantity())
                .referenceType("RESERVATION_FULFILL")
                .referenceId(reservation.getOrderId())
                .userId(userId)
                .notes("Fulfilled reservation #" + reservation.getId() + " for order " + reservation.getOrderId())
                .build();
        stockMovementRepository.save(movement);
        wmsMetrics.incrementStockMovement(MovementType.ISSUE);

        log.info("Fulfilled reservation #{} for order {}", reservation.getId(), reservation.getOrderId());
        return mapToResponse(updated);
    }

    @Transactional
    public ReservationResponse releaseReservation(Long reservationId, String userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new InvalidOperationException("Cannot release reservation in status: " + reservation.getStatus());
        }

        StockItem item = stockItemRepository.findByLocationId(reservation.getLocation().getId()).stream()
                .filter(i -> i.getProduct().getId().equals(reservation.getProduct().getId()))
                .findFirst()
                .orElse(null);

        if (item != null) {
            item.setQuantityReserved(Math.max(0, item.getQuantityReserved() - reservation.getQuantity()));
            stockItemRepository.save(item);
        }

        reservation.setStatus(ReservationStatus.RELEASED);
        Reservation updated = reservationRepository.save(reservation);

        StockMovement movement = StockMovement.builder()
                .product(reservation.getProduct())
                .location(reservation.getLocation())
                .type(MovementType.RELEASE)
                .quantity(0)
                .referenceType("RESERVATION_RELEASE")
                .referenceId(reservation.getOrderId())
                .userId(userId)
                .notes("Released reservation #" + reservation.getId() + " for order " + reservation.getOrderId())
                .build();
        stockMovementRepository.save(movement);

        log.info("Released reservation #{} for order {}", reservation.getId(), reservation.getOrderId());
        return mapToResponse(updated);
    }

    @Transactional
    public int expireStaleReservations() {
        List<Reservation> staleReservations = reservationRepository.findExpiredReservations(
                ReservationStatus.ACTIVE, Instant.now()
        );

        int count = 0;
        for (Reservation reservation : staleReservations) {
            try {
                StockItem item = stockItemRepository.findByLocationId(reservation.getLocation().getId()).stream()
                        .filter(i -> i.getProduct().getId().equals(reservation.getProduct().getId()))
                        .findFirst()
                        .orElse(null);

                if (item != null) {
                    item.setQuantityReserved(Math.max(0, item.getQuantityReserved() - reservation.getQuantity()));
                    stockItemRepository.save(item);
                }

                reservation.setStatus(ReservationStatus.EXPIRED);
                reservationRepository.save(reservation);

                StockMovement movement = StockMovement.builder()
                        .product(reservation.getProduct())
                        .location(reservation.getLocation())
                        .type(MovementType.RELEASE)
                        .quantity(0)
                        .referenceType("RESERVATION_EXPIRED")
                        .referenceId(reservation.getOrderId())
                        .notes("Expired stale reservation #" + reservation.getId() + " for order " + reservation.getOrderId())
                        .build();
                stockMovementRepository.save(movement);

                count++;
            } catch (Exception ex) {
                log.error("Error expiring reservation #{}: {}", reservation.getId(), ex.getMessage());
            }
        }

        if (count > 0) {
            log.info("Expired and released {} stale reservations", count);
        }
        return count;
    }

    private void recordBackorder(String orderId, Product product, int requested, int available) {
        int pending = requested - available;
        Backorder backorder = Backorder.builder()
                .orderId(orderId)
                .product(product)
                .quantityOrdered(requested)
                .quantityFulfilled(Math.max(0, available))
                .quantityPending(pending)
                .status(BackorderStatus.PENDING)
                .build();
        backorderRepository.save(backorder);
        log.warn("Created Backorder for order {}: {} units of SKU {}", orderId, pending, product.getSku());
    }

    private ReservationResponse mapToResponse(Reservation r) {
        return ReservationResponse.builder()
                .id(r.getId())
                .orderId(r.getOrderId())
                .productId(r.getProduct().getId())
                .productSku(r.getProduct().getSku())
                .productName(r.getProduct().getName())
                .locationId(r.getLocation().getId())
                .locationCode(r.getLocation().getCode())
                .warehouseCode(r.getLocation().getWarehouse().getCode())
                .quantity(r.getQuantity())
                .status(r.getStatus())
                .expiresAt(r.getExpiresAt())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
