package com.stockroom.wms.controller;

import com.stockroom.wms.domain.BackorderStatus;
import com.stockroom.wms.dto.backorder.BackorderDTOs.*;
import com.stockroom.wms.service.BackorderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/backorders")
@Tag(name = "Backorders", description = "Endpoints for managing customer and distributor backorders")
public class BackorderController {

    private final BackorderService backorderService;

    public BackorderController(BackorderService backorderService) {
        this.backorderService = backorderService;
    }

    @Operation(summary = "Get list of backorders with optional status filter")
    @GetMapping
    public ResponseEntity<List<BackorderResponse>> getBackorders(
            @RequestParam(required = false) BackorderStatus status
    ) {
        return ResponseEntity.ok(backorderService.getBackorders(status));
    }

    @Operation(summary = "Fulfill backorder quantity from specific warehouse location bin")
    @PostMapping("/{id}/fulfill")
    public ResponseEntity<BackorderResponse> fulfillBackorder(
            @PathVariable Long id,
            @Valid @RequestBody FulfillBackorderRequest request
    ) {
        return ResponseEntity.ok(backorderService.fulfillBackorder(id, request));
    }
}
