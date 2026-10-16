package com.stockroom.wms.service;

import com.stockroom.wms.config.WmsMetrics;
import com.stockroom.wms.domain.*;
import com.stockroom.wms.dto.returns.ReturnDTOs.*;
import com.stockroom.wms.exception.InvalidOperationException;
import com.stockroom.wms.exception.ResourceNotFoundException;
import com.stockroom.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class ReturnService {

    private final StockReturnRepository returnRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WmsMetrics wmsMetrics;

    public ReturnService(
            StockReturnRepository returnRepository,
            ProductRepository productRepository,
            LocationRepository locationRepository,
            StockItemRepository stockItemRepository,
            StockMovementRepository stockMovementRepository,
            WmsMetrics wmsMetrics
    ) {
        this.returnRepository = returnRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.stockItemRepository = stockItemRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.wmsMetrics = wmsMetrics;
    }

    @Transactional
    public ReturnResponse createReturn(CreateReturnRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));

        StockReturn stockReturn = StockReturn.builder()
                .originalOrderId(request.getOriginalOrderId().trim())
                .product(product)
                .quantity(request.getQuantity())
                .reason(request.getReason())
                .status(ReturnStatus.PENDING)
                .build();

        StockReturn saved = returnRepository.save(stockReturn);
        log.info("Registered Return #{} for order {}, reason: {}", saved.getId(), saved.getOriginalOrderId(), saved.getReason());
        return mapToResponse(saved);
    }

    @Transactional
    public ReturnResponse inspectReturn(Long id, InspectReturnRequest request) {
        StockReturn stockReturn = returnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found: " + id));

        if (stockReturn.getStatus() != ReturnStatus.PENDING) {
            throw new InvalidOperationException("Return is not in PENDING state; current status: " + stockReturn.getStatus());
        }

        stockReturn.setInspectionNotes(request.getInspectionNotes());
        stockReturn.setStatus(ReturnStatus.INSPECTED);

        StockReturn updated = returnRepository.save(stockReturn);
        log.info("Inspected Return #{}: notes='{}'", id, request.getInspectionNotes());
        return mapToResponse(updated);
    }

    @Transactional
    public ReturnResponse restockReturn(Long id, RestockReturnRequest request, String userId) {
        StockReturn stockReturn = returnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found: " + id));

        if (stockReturn.getStatus() != ReturnStatus.INSPECTED && stockReturn.getStatus() != ReturnStatus.PENDING) {
            throw new InvalidOperationException("Cannot restock return in status: " + stockReturn.getStatus());
        }

        Location targetLocation = locationRepository.findById(request.getTargetLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Target location not found: " + request.getTargetLocationId()));

        String batch = request.getBatchNumber().trim();

        // Increment stock item
        StockItem item = stockItemRepository.findByProductIdAndLocationIdAndBatchNumber(
                stockReturn.getProduct().getId(), targetLocation.getId(), batch
        ).orElseGet(() -> StockItem.builder()
                .product(stockReturn.getProduct())
                .location(targetLocation)
                .batchNumber(batch)
                .quantityOnHand(0)
                .quantityReserved(0)
                .build());

        item.setQuantityOnHand(item.getQuantityOnHand() + stockReturn.getQuantity());
        stockItemRepository.save(item);

        // Record RETURN movement into immutable ledger
        StockMovement movement = StockMovement.builder()
                .product(stockReturn.getProduct())
                .location(targetLocation)
                .type(MovementType.RETURN)
                .quantity(stockReturn.getQuantity())
                .referenceType("CUSTOMER_RETURN_RESTOCK")
                .referenceId("RET-" + stockReturn.getId())
                .userId(userId)
                .notes("Restocked returned items from order " + stockReturn.getOriginalOrderId() + " into " + targetLocation.getCode())
                .build();
        stockMovementRepository.save(movement);
        wmsMetrics.incrementStockMovement(MovementType.RETURN);

        stockReturn.setStatus(ReturnStatus.RESTOCKED);
        StockReturn updated = returnRepository.save(stockReturn);

        log.info("Restocked {} units of Return #{} into location {}",
                stockReturn.getQuantity(), id, targetLocation.getCode());

        return mapToResponse(updated);
    }

    @Transactional
    public ReturnResponse scrapReturn(Long id, String notes) {
        StockReturn stockReturn = returnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found: " + id));

        if (stockReturn.getStatus() == ReturnStatus.RESTOCKED || stockReturn.getStatus() == ReturnStatus.SCRAPPED) {
            throw new InvalidOperationException("Return is already finalized with status: " + stockReturn.getStatus());
        }

        stockReturn.setStatus(ReturnStatus.SCRAPPED);
        if (notes != null) {
            stockReturn.setInspectionNotes(
                    (stockReturn.getInspectionNotes() != null ? stockReturn.getInspectionNotes() + "; " : "") +
                            "Scrapped: " + notes
            );
        }

        StockReturn updated = returnRepository.save(stockReturn);
        log.info("Scrapped Return #{}: {}", id, notes);
        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> getAllReturns(ReturnStatus status) {
        List<StockReturn> list = (status != null)
                ? returnRepository.findByStatus(status)
                : returnRepository.findAll();

        return list.stream().map(this::mapToResponse).toList();
    }

    private ReturnResponse mapToResponse(StockReturn r) {
        return ReturnResponse.builder()
                .id(r.getId())
                .originalOrderId(r.getOriginalOrderId())
                .productId(r.getProduct().getId())
                .productSku(r.getProduct().getSku())
                .productName(r.getProduct().getName())
                .quantity(r.getQuantity())
                .reason(r.getReason())
                .status(r.getStatus())
                .inspectionNotes(r.getInspectionNotes())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
