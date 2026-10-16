package com.stockroom.wms.service;

import com.stockroom.wms.domain.Backorder;
import com.stockroom.wms.domain.BackorderStatus;
import com.stockroom.wms.domain.StockItem;
import com.stockroom.wms.dto.backorder.BackorderDTOs.*;
import com.stockroom.wms.exception.InsufficientStockException;
import com.stockroom.wms.exception.InvalidOperationException;
import com.stockroom.wms.exception.ResourceNotFoundException;
import com.stockroom.wms.repository.BackorderRepository;
import com.stockroom.wms.repository.StockItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class BackorderService {

    private final BackorderRepository backorderRepository;
    private final StockItemRepository stockItemRepository;

    public BackorderService(BackorderRepository backorderRepository, StockItemRepository stockItemRepository) {
        this.backorderRepository = backorderRepository;
        this.stockItemRepository = stockItemRepository;
    }

    @Transactional(readOnly = true)
    public List<BackorderResponse> getBackorders(BackorderStatus status) {
        List<Backorder> list = (status != null)
                ? backorderRepository.findByStatus(status)
                : backorderRepository.findAll();

        return list.stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public BackorderResponse fulfillBackorder(Long backorderId, FulfillBackorderRequest request) {
        Backorder backorder = backorderRepository.findById(backorderId)
                .orElseThrow(() -> new ResourceNotFoundException("Backorder not found: " + backorderId));

        if (backorder.getStatus() == BackorderStatus.FULFILLED || backorder.getStatus() == BackorderStatus.CANCELLED) {
            throw new InvalidOperationException("Backorder is already " + backorder.getStatus());
        }

        if (request.getQuantity() > backorder.getQuantityPending()) {
            throw new InvalidOperationException(String.format(
                    "Fulfill quantity (%d) cannot exceed pending quantity (%d)",
                    request.getQuantity(), backorder.getQuantityPending()
            ));
        }

        StockItem item = stockItemRepository.findByLocationId(request.getLocationId()).stream()
                .filter(i -> i.getProduct().getId().equals(backorder.getProduct().getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No stock for product at specified location"));

        if (item.getQuantityAvailable() < request.getQuantity()) {
            throw new InsufficientStockException(String.format(
                    "Insufficient stock at location. Available: %d, Requested: %d",
                    item.getQuantityAvailable(), request.getQuantity()
            ));
        }

        // Deduct from stock
        item.setQuantityOnHand(item.getQuantityOnHand() - request.getQuantity());
        stockItemRepository.save(item);

        backorder.setQuantityFulfilled(backorder.getQuantityFulfilled() + request.getQuantity());
        backorder.setQuantityPending(backorder.getQuantityPending() - request.getQuantity());

        if (backorder.getQuantityPending() == 0) {
            backorder.setStatus(BackorderStatus.FULFILLED);
        } else {
            backorder.setStatus(BackorderStatus.PARTIALLY_FULFILLED);
        }

        Backorder updated = backorderRepository.save(backorder);
        log.info("Fulfilled {} units of Backorder #{}, status: {}",
                request.getQuantity(), backorder.getId(), backorder.getStatus());

        return mapToResponse(updated);
    }

    private BackorderResponse mapToResponse(Backorder b) {
        return BackorderResponse.builder()
                .id(b.getId())
                .orderId(b.getOrderId())
                .productId(b.getProduct().getId())
                .productSku(b.getProduct().getSku())
                .productName(b.getProduct().getName())
                .quantityOrdered(b.getQuantityOrdered())
                .quantityFulfilled(b.getQuantityFulfilled())
                .quantityPending(b.getQuantityPending())
                .status(b.getStatus())
                .createdAt(b.getCreatedAt())
                .build();
    }
}
