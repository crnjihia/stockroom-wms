package com.stockroom.wms.controller;

import com.stockroom.wms.dto.barcode.BarcodeDTOs.*;
import com.stockroom.wms.service.BarcodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/barcode")
@Tag(name = "Barcode & GS1 Operations", description = "Endpoints for parsing GS1-128 barcodes, scan simulation, and barcode generation")
public class BarcodeController {

    private final BarcodeService barcodeService;

    public BarcodeController(BarcodeService barcodeService) {
        this.barcodeService = barcodeService;
    }

    @Operation(summary = "Scan GS1-128 barcode simulation: parses AIs, validates GTIN check digit, and updates stock")
    @PostMapping("/scan")
    public ResponseEntity<BarcodeScanResponse> scanBarcode(
            @Valid @RequestBody BarcodeScanRequest request,
            Authentication authentication
    ) {
        String userId = authentication != null ? authentication.getName() : "scanner-handheld";
        return ResponseEntity.ok(barcodeService.scanBarcode(request, userId));
    }

    @Operation(summary = "Generate GS1-128 barcode PNG image for product")
    @GetMapping(value = "/generate/{productId}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> generateBarcode(@PathVariable("productId") Long productId) {
        byte[] imageBytes = barcodeService.generateBarcodeImage(productId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"barcode_product_" + productId + ".png\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(imageBytes);
    }
}
