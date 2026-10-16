package com.stockroom.wms.service;

import com.stockroom.wms.config.WmsMetrics;
import com.stockroom.wms.domain.*;
import com.stockroom.wms.dto.stock.StockDTOs.*;
import com.stockroom.wms.exception.InsufficientStockException;
import com.stockroom.wms.exception.InvalidOperationException;
import com.stockroom.wms.exception.LedgerDiscrepancyException;
import com.stockroom.wms.exception.ResourceNotFoundException;
import com.stockroom.wms.repository.LocationRepository;
import com.stockroom.wms.repository.ProductRepository;
import com.stockroom.wms.repository.StockItemRepository;
import com.stockroom.wms.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class StockService {

    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WmsMetrics wmsMetrics;

    public StockService(
            ProductRepository productRepository,
            LocationRepository locationRepository,
            StockItemRepository stockItemRepository,
            StockMovementRepository stockMovementRepository,
            WmsMetrics wmsMetrics
    ) {
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.stockItemRepository = stockItemRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.wmsMetrics = wmsMetrics;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'PICKER')")
    @Transactional
    public StockMovementResponse receiveStock(ReceiveStockRequest request, String userId) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + request.getLocationId()));

        String batchNumber = request.getBatchNumber().trim();

        StockItem stockItem = stockItemRepository.findByProductIdAndLocationIdAndBatchNumber(
                product.getId(), location.getId(), batchNumber
        ).orElseGet(() -> StockItem.builder()
                .product(product)
                .location(location)
                .batchNumber(batchNumber)
                .expiryDate(request.getExpiryDate())
                .quantityOnHand(0)
                .quantityReserved(0)
                .build());

        stockItem.setQuantityOnHand(stockItem.getQuantityOnHand() + request.getQuantity());
        if (request.getExpiryDate() != null) {
            stockItem.setExpiryDate(request.getExpiryDate());
        }
        stockItemRepository.save(stockItem);

        StockMovement movement = StockMovement.builder()
                .product(product)
                .location(location)
                .type(MovementType.RECEIPT)
                .quantity(request.getQuantity())
                .referenceType("PURCHASE_RECEIPT")
                .referenceId(request.getReference() != null ? request.getReference() : "GRN-" + System.currentTimeMillis())
                .userId(userId)
                .notes("Stock received at location " + location.getCode() + ", batch: " + batchNumber)
                .build();

        StockMovement savedMovement = stockMovementRepository.save(movement);
        wmsMetrics.incrementStockMovement(MovementType.RECEIPT);

        log.info("Received {} units of SKU {} into location {}, batch {}",
                request.getQuantity(), product.getSku(), location.getCode(), batchNumber);

        return mapToMovementResponse(savedMovement);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'PICKER')")
    @Transactional
    public StockMovementResponse issueStock(IssueStockRequest request, String userId) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + request.getLocationId()));

        List<StockItem> items;
        if (request.getBatchNumber() != null && !request.getBatchNumber().isBlank()) {
            items = stockItemRepository.findByProductIdAndLocationIdAndBatchNumber(
                    product.getId(), location.getId(), request.getBatchNumber().trim()
            ).map(List::of).orElse(List.of());
        } else {
            items = stockItemRepository.findByLocationId(location.getId()).stream()
                    .filter(item -> item.getProduct().getId().equals(product.getId()) && item.getQuantityOnHand() > 0)
                    .toList();
        }

        int availableInLocation = items.stream().mapToInt(StockItem::getQuantityOnHand).sum();
        if (availableInLocation < request.getQuantity()) {
            throw new InsufficientStockException(String.format(
                    "Insufficient stock at location %s for product %s. Requested: %d, Available on hand: %d",
                    location.getCode(), product.getSku(), request.getQuantity(), availableInLocation
            ));
        }

        // Deduct quantity across items (FIFO)
        int remainingToDeduct = request.getQuantity();
        for (StockItem item : items) {
            if (remainingToDeduct <= 0) break;
            int deduct = Math.min(item.getQuantityOnHand(), remainingToDeduct);
            item.setQuantityOnHand(item.getQuantityOnHand() - deduct);
            remainingToDeduct -= deduct;
            stockItemRepository.save(item);
        }

        StockMovement movement = StockMovement.builder()
                .product(product)
                .location(location)
                .type(MovementType.ISSUE)
                .quantity(request.getQuantity())
                .referenceType("DISPATCH")
                .referenceId(request.getReference() != null ? request.getReference() : "DO-" + System.currentTimeMillis())
                .userId(userId)
                .notes("Stock issued from location " + location.getCode())
                .build();

        StockMovement savedMovement = stockMovementRepository.save(movement);
        wmsMetrics.incrementStockMovement(MovementType.ISSUE);

        log.info("Issued {} units of SKU {} from location {}",
                request.getQuantity(), product.getSku(), location.getCode());

        return mapToMovementResponse(savedMovement);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER', 'PICKER')")
    @Transactional
    public List<StockMovementResponse> transferStock(TransferStockRequest request, String userId) {
        if (request.getFromLocationId().equals(request.getToLocationId())) {
            throw new InvalidOperationException("Source and destination locations must be different");
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Source location not found: " + request.getFromLocationId()));

        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination location not found: " + request.getToLocationId()));

        String batch = request.getBatchNumber().trim();

        StockItem sourceItem = stockItemRepository.findByProductIdAndLocationIdAndBatchNumber(
                product.getId(), fromLocation.getId(), batch
        ).orElseThrow(() -> new InsufficientStockException("No stock found for batch " + batch + " at source location"));

        if (sourceItem.getQuantityAvailable() < request.getQuantity()) {
            throw new InsufficientStockException(String.format(
                    "Insufficient available stock at source location %s for batch %s. Available: %d, Requested: %d",
                    fromLocation.getCode(), batch, sourceItem.getQuantityAvailable(), request.getQuantity()
            ));
        }

        // Deduct from source
        sourceItem.setQuantityOnHand(sourceItem.getQuantityOnHand() - request.getQuantity());
        stockItemRepository.save(sourceItem);

        // Add to destination
        StockItem destItem = stockItemRepository.findByProductIdAndLocationIdAndBatchNumber(
                product.getId(), toLocation.getId(), batch
        ).orElseGet(() -> StockItem.builder()
                .product(product)
                .location(toLocation)
                .batchNumber(batch)
                .expiryDate(sourceItem.getExpiryDate())
                .quantityOnHand(0)
                .quantityReserved(0)
                .build());

        destItem.setQuantityOnHand(destItem.getQuantityOnHand() + request.getQuantity());
        stockItemRepository.save(destItem);

        String transferId = "TRF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Two linked movements: ISSUE at source, RECEIPT at destination
        StockMovement outboundMovement = StockMovement.builder()
                .product(product)
                .location(fromLocation)
                .type(MovementType.TRANSFER)
                .quantity(-request.getQuantity())
                .referenceType("STOCK_TRANSFER_OUT")
                .referenceId(transferId)
                .userId(userId)
                .notes("Transfer outbound to " + toLocation.getCode() + " (wh: " + toLocation.getWarehouse().getCode() + "). " + (request.getNotes() != null ? request.getNotes() : ""))
                .build();

        StockMovement inboundMovement = StockMovement.builder()
                .product(product)
                .location(toLocation)
                .type(MovementType.TRANSFER)
                .quantity(request.getQuantity())
                .referenceType("STOCK_TRANSFER_IN")
                .referenceId(transferId)
                .userId(userId)
                .notes("Transfer inbound from " + fromLocation.getCode() + " (wh: " + fromLocation.getWarehouse().getCode() + "). " + (request.getNotes() != null ? request.getNotes() : ""))
                .build();

        StockMovement savedOut = stockMovementRepository.save(outboundMovement);
        StockMovement savedIn = stockMovementRepository.save(inboundMovement);

        wmsMetrics.incrementStockMovement(MovementType.TRANSFER);

        log.info("Stock transfer {} completed: {} units of SKU {} from {} to {}",
                transferId, request.getQuantity(), product.getSku(), fromLocation.getCode(), toLocation.getCode());

        return List.of(mapToMovementResponse(savedOut), mapToMovementResponse(savedIn));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER')")
    @Transactional
    public StockMovementResponse adjustStock(AdjustStockRequest request, String userId) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + request.getLocationId()));

        String batch = request.getBatchNumber().trim();

        StockItem item = stockItemRepository.findByProductIdAndLocationIdAndBatchNumber(
                product.getId(), location.getId(), batch
        ).orElseGet(() -> StockItem.builder()
                .product(product)
                .location(location)
                .batchNumber(batch)
                .quantityOnHand(0)
                .quantityReserved(0)
                .build());

        int currentOnHand = item.getQuantityOnHand();
        int delta = request.getNewQuantity() - currentOnHand;

        if (delta == 0) {
            throw new InvalidOperationException("New quantity matches current quantity on hand; no adjustment needed");
        }

        if (request.getNewQuantity() < item.getQuantityReserved()) {
            throw new InvalidOperationException(String.format(
                    "New quantity (%d) cannot be less than currently reserved quantity (%d)",
                    request.getNewQuantity(), item.getQuantityReserved()
            ));
        }

        item.setQuantityOnHand(request.getNewQuantity());
        stockItemRepository.save(item);

        StockMovement movement = StockMovement.builder()
                .product(product)
                .location(location)
                .type(MovementType.ADJUSTMENT)
                .quantity(delta)
                .referenceType("CYCLE_COUNT_ADJUSTMENT")
                .referenceId("ADJ-" + System.currentTimeMillis())
                .userId(userId)
                .notes("Inventory adjustment: " + request.getReason() + " (Prev: " + currentOnHand + ", New: " + request.getNewQuantity() + ", Delta: " + delta + ")")
                .build();

        StockMovement saved = stockMovementRepository.save(movement);
        wmsMetrics.incrementStockMovement(MovementType.ADJUSTMENT);

        log.info("Adjusted stock for SKU {} at {} (Batch {}): delta={}, newTotal={}",
                product.getSku(), location.getCode(), batch, delta, request.getNewQuantity());

        return mapToMovementResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> getMovements(Long productId, Long locationId, Instant from, Instant to) {
        Specification<StockMovement> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (productId != null) {
                predicates.add(cb.equal(root.get("product").get("id"), productId));
            }
            if (locationId != null) {
                predicates.add(cb.equal(root.get("location").get("id"), locationId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return stockMovementRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::mapToMovementResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReconciliationResult reconcileLedger(Long productId, Long locationId) {
        List<StockItem> items = stockItemRepository.findByLocationId(locationId).stream()
                .filter(i -> i.getProduct().getId().equals(productId))
                .toList();

        int currentOnHand = items.stream().mapToInt(StockItem::getQuantityOnHand).sum();
        int movementSum = stockMovementRepository.calculateNetOnHandMovements(productId, locationId);

        boolean balanced = (currentOnHand == movementSum);
        if (!balanced) {
            String errorMsg = String.format("Ledger Discrepancy detected for Product ID %d at Location ID %d! QuantityOnHand: %d, MovementsSum: %d",
                    productId, locationId, currentOnHand, movementSum);
            log.error(errorMsg);
            throw new LedgerDiscrepancyException(errorMsg);
        }

        return ReconciliationResult.builder()
                .productId(productId)
                .locationId(locationId)
                .currentOnHand(currentOnHand)
                .calculatedMovementsSum(movementSum)
                .isBalanced(true)
                .status("BALANCED")
                .build();
    }

    private StockMovementResponse mapToMovementResponse(StockMovement m) {
        return StockMovementResponse.builder()
                .id(m.getId())
                .productId(m.getProduct().getId())
                .productSku(m.getProduct().getSku())
                .productName(m.getProduct().getName())
                .locationId(m.getLocation().getId())
                .locationCode(m.getLocation().getCode())
                .warehouseCode(m.getLocation().getWarehouse().getCode())
                .type(m.getType())
                .quantity(m.getQuantity())
                .referenceType(m.getReferenceType())
                .referenceId(m.getReferenceId())
                .userId(m.getUserId())
                .notes(m.getNotes())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
