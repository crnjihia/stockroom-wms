package com.stockroom.wms.controller;

import com.stockroom.wms.dto.stock.StockDTOs.*;
import com.stockroom.wms.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/stock")
@Tag(name = "Stock Operations", description = "Endpoints for receiving, issuing, transferring, and adjusting inventory")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @Operation(summary = "Receive stock into warehouse location bin")
    @PostMapping("/receive")
    public ResponseEntity<StockMovementResponse> receiveStock(
            @Valid @RequestBody ReceiveStockRequest request,
            Authentication authentication
    ) {
        String userId = authentication != null ? authentication.getName() : "system";
        return ResponseEntity.status(HttpStatus.CREATED).body(stockService.receiveStock(request, userId));
    }

    @Operation(summary = "Issue/dispatch stock from warehouse location bin")
    @PostMapping("/issue")
    public ResponseEntity<StockMovementResponse> issueStock(
            @Valid @RequestBody IssueStockRequest request,
            Authentication authentication
    ) {
        String userId = authentication != null ? authentication.getName() : "system";
        return ResponseEntity.ok(stockService.issueStock(request, userId));
    }

    @Operation(summary = "Transfer stock between location bins or depots")
    @PostMapping("/transfer")
    public ResponseEntity<List<StockMovementResponse>> transferStock(
            @Valid @RequestBody TransferStockRequest request,
            Authentication authentication
    ) {
        String userId = authentication != null ? authentication.getName() : "system";
        return ResponseEntity.ok(stockService.transferStock(request, userId));
    }

    @Operation(summary = "Adjust inventory count with required audit reason (MANAGER or ADMIN only)")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    @PostMapping("/adjust")
    public ResponseEntity<StockMovementResponse> adjustStock(
            @Valid @RequestBody AdjustStockRequest request,
            Authentication authentication
    ) {
        String userId = authentication != null ? authentication.getName() : "system";
        return ResponseEntity.ok(stockService.adjustStock(request, userId));
    }

    @Operation(summary = "Query immutable stock movements ledger")
    @GetMapping("/movements")
    public ResponseEntity<List<StockMovementResponse>> getMovements(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(stockService.getMovements(productId, locationId, from, to));
    }

    @Operation(summary = "Trigger ledger reconciliation assertion for a specific product and location")
    @PostMapping("/reconcile")
    public ResponseEntity<ReconciliationResult> reconcileLedger(
            @RequestParam Long productId,
            @RequestParam Long locationId
    ) {
        return ResponseEntity.ok(stockService.reconcileLedger(productId, locationId));
    }
}
