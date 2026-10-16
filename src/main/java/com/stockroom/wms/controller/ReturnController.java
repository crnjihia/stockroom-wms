package com.stockroom.wms.controller;

import com.stockroom.wms.domain.ReturnStatus;
import com.stockroom.wms.dto.returns.ReturnDTOs.*;
import com.stockroom.wms.service.ReturnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/returns")
@Tag(name = "Returns Management", description = "Endpoints for handling damaged, expired, or customer returns")
public class ReturnController {

    private final ReturnService returnService;

    public ReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @Operation(summary = "Register a new stock return from an order")
    @PostMapping
    public ResponseEntity<ReturnResponse> createReturn(@Valid @RequestBody CreateReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(returnService.createReturn(request));
    }

    @Operation(summary = "Get list of returns with optional status filter")
    @GetMapping
    public ResponseEntity<List<ReturnResponse>> getReturns(
            @RequestParam(required = false) ReturnStatus status
    ) {
        return ResponseEntity.ok(returnService.getAllReturns(status));
    }

    @Operation(summary = "Record quality inspection results and notes for a return")
    @PostMapping("/{id}/inspect")
    public ResponseEntity<ReturnResponse> inspectReturn(
            @PathVariable Long id,
            @Valid @RequestBody InspectReturnRequest request
    ) {
        return ResponseEntity.ok(returnService.inspectReturn(id, request));
    }

    @Operation(summary = "Restock inspected returned items into warehouse storage bin")
    @PostMapping("/{id}/restock")
    public ResponseEntity<ReturnResponse> restockReturn(
            @PathVariable Long id,
            @Valid @RequestBody RestockReturnRequest request,
            Authentication authentication
    ) {
        String userId = authentication != null ? authentication.getName() : "system";
        return ResponseEntity.ok(returnService.restockReturn(id, request, userId));
    }

    @Operation(summary = "Scrap damaged or expired return items")
    @PostMapping("/{id}/scrap")
    public ResponseEntity<ReturnResponse> scrapReturn(
            @PathVariable Long id,
            @RequestParam(required = false) String notes
    ) {
        return ResponseEntity.ok(returnService.scrapReturn(id, notes));
    }
}
