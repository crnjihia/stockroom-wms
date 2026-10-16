package com.stockroom.wms.service;

import com.stockroom.wms.domain.Product;
import com.stockroom.wms.domain.StockItem;
import com.stockroom.wms.domain.Warehouse;
import com.stockroom.wms.dto.product.ProductDTOs.*;
import com.stockroom.wms.exception.InvalidOperationException;
import com.stockroom.wms.exception.ResourceNotFoundException;
import com.stockroom.wms.repository.ProductRepository;
import com.stockroom.wms.repository.StockItemRepository;
import com.stockroom.wms.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final StockItemRepository stockItemRepository;
    private final WarehouseRepository warehouseRepository;

    public ProductService(
            ProductRepository productRepository,
            StockItemRepository stockItemRepository,
            WarehouseRepository warehouseRepository
    ) {
        this.productRepository = productRepository;
        this.stockItemRepository = stockItemRepository;
        this.warehouseRepository = warehouseRepository;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER')")
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new InvalidOperationException("Product SKU '" + request.getSku() + "' already exists");
        }

        Product product = Product.builder()
                .sku(request.getSku().trim())
                .name(request.getName().trim())
                .description(request.getDescription())
                .category(request.getCategory().trim())
                .unitOfMeasure(request.getUnitOfMeasure().trim().toUpperCase())
                .barcode(request.getBarcode().trim())
                .reorderPoint(request.getReorderPoint())
                .reorderQty(request.getReorderQty())
                .unitCost(request.getUnitCost())
                .unitPrice(request.getUnitPrice())
                .build();

        Product saved = productRepository.save(product);
        log.info("Created product SKU: {} with ID {}", saved.getSku(), saved.getId());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(String query, String category, Pageable pageable) {
        if ((query == null || query.isBlank()) && (category == null || category.isBlank())) {
            return productRepository.findAll(pageable).map(this::mapToResponse);
        }
        return productRepository.searchProducts(
                query != null && !query.isBlank() ? query.trim() : null,
                category != null && !category.isBlank() ? category.trim() : null,
                pageable
        ).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return mapToResponse(product);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER')")
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (!product.getSku().equalsIgnoreCase(request.getSku().trim()) &&
                productRepository.existsBySku(request.getSku().trim())) {
            throw new InvalidOperationException("Cannot update: SKU '" + request.getSku() + "' already exists");
        }

        product.setSku(request.getSku().trim());
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory().trim());
        product.setUnitOfMeasure(request.getUnitOfMeasure().trim().toUpperCase());
        product.setBarcode(request.getBarcode().trim());
        product.setReorderPoint(request.getReorderPoint());
        product.setReorderQty(request.getReorderQty());
        product.setUnitCost(request.getUnitCost());
        product.setUnitPrice(request.getUnitPrice());

        Product updated = productRepository.save(product);
        log.info("Updated product ID: {}, SKU: {}", updated.getId(), updated.getSku());
        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    public ProductStockSummaryResponse getProductStock(Long productId, Long warehouseId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        List<StockItem> stockItems;
        if (warehouseId != null) {
            stockItems = stockItemRepository.findByProductIdAndLocationWarehouseId(productId, warehouseId);
        } else {
            stockItems = stockItemRepository.findByProductId(productId);
        }

        int totalOnHand = 0;
        int totalReserved = 0;

        Map<Long, List<StockItem>> itemsByWarehouse = stockItems.stream()
                .collect(Collectors.groupingBy(item -> item.getLocation().getWarehouse().getId()));

        List<WarehouseStockBreakdown> warehouseBreakdowns = new ArrayList<>();

        for (Map.Entry<Long, List<StockItem>> entry : itemsByWarehouse.entrySet()) {
            List<StockItem> items = entry.getValue();
            Warehouse wh = items.get(0).getLocation().getWarehouse();

            int whOnHand = items.stream().mapToInt(StockItem::getQuantityOnHand).sum();
            int whReserved = items.stream().mapToInt(StockItem::getQuantityReserved).sum();

            totalOnHand += whOnHand;
            totalReserved += whReserved;

            List<LocationStockDetail> locationDetails = items.stream().map(item ->
                    LocationStockDetail.builder()
                            .stockItemId(item.getId())
                            .locationId(item.getLocation().getId())
                            .locationCode(item.getLocation().getCode())
                            .zone(item.getLocation().getZone())
                            .batchNumber(item.getBatchNumber())
                            .expiryDate(item.getExpiryDate())
                            .quantityOnHand(item.getQuantityOnHand())
                            .quantityReserved(item.getQuantityReserved())
                            .quantityAvailable(item.getQuantityAvailable())
                            .build()
            ).toList();

            warehouseBreakdowns.add(WarehouseStockBreakdown.builder()
                    .warehouseId(wh.getId())
                    .warehouseCode(wh.getCode())
                    .warehouseName(wh.getName())
                    .onHand(whOnHand)
                    .reserved(whReserved)
                    .available(Math.max(0, whOnHand - whReserved))
                    .items(locationDetails)
                    .build());
        }

        return ProductStockSummaryResponse.builder()
                .productId(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .totalOnHand(totalOnHand)
                .totalReserved(totalReserved)
                .totalAvailable(Math.max(0, totalOnHand - totalReserved))
                .warehouses(warehouseBreakdowns)
                .build();
    }

    private ProductResponse mapToResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .sku(p.getSku())
                .name(p.getName())
                .description(p.getDescription())
                .category(p.getCategory())
                .unitOfMeasure(p.getUnitOfMeasure())
                .barcode(p.getBarcode())
                .reorderPoint(p.getReorderPoint())
                .reorderQty(p.getReorderQty())
                .unitCost(p.getUnitCost())
                .unitPrice(p.getUnitPrice())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
