package com.stockroom.wms.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.stockroom.wms.domain.Location;
import com.stockroom.wms.domain.Product;
import com.stockroom.wms.dto.barcode.BarcodeDTOs.*;
import com.stockroom.wms.dto.stock.StockDTOs.*;
import com.stockroom.wms.exception.InvalidBarcodeException;
import com.stockroom.wms.exception.ResourceNotFoundException;
import com.stockroom.wms.repository.LocationRepository;
import com.stockroom.wms.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class BarcodeService {

    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final StockService stockService;

    public BarcodeService(
            ProductRepository productRepository,
            LocationRepository locationRepository,
            StockService stockService
    ) {
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.stockService = stockService;
    }

    // Pattern to match parenthesized Application Identifiers, e.g. (01)06161101234567(10)BAT-123(17)270630(21)SN1001
    private static final Pattern PAREN_AI_PATTERN = Pattern.compile("\\((\\d{2})\\)([^\\(\\)]+)");

    /**
     * Parses GS1-128 barcode string into structured components and validates GTIN check digit.
     */
    public ParsedGs1Data parseGs1Barcode(String barcodeString) {
        if (barcodeString == null || barcodeString.trim().isEmpty()) {
            throw new InvalidBarcodeException("Barcode string cannot be empty");
        }

        String raw = barcodeString.trim();
        String gtin = null;
        String batchNumber = null;
        LocalDate expiryDate = null;
        String serialNumber = null;

        Matcher matcher = PAREN_AI_PATTERN.matcher(raw);
        boolean matchedAny = false;

        while (matcher.find()) {
            matchedAny = true;
            String ai = matcher.group(1);
            String value = matcher.group(2).trim();

            switch (ai) {
                case "01" -> gtin = padGtin14(value);
                case "10" -> batchNumber = value;
                case "17" -> expiryDate = parseGs1Date(value);
                case "21" -> serialNumber = value;
                default -> log.debug("Ignored unhandled GS1 AI ({}) with value {}", ai, value);
            }
        }

        // If not parenthesized, check if it's a plain GTIN-13/14 or composite unbracketed string
        if (!matchedAny) {
            if (raw.startsWith("01") && raw.length() >= 16) {
                gtin = raw.substring(2, 16);
                String rest = raw.substring(16);
                if (rest.startsWith("17") && rest.length() >= 8) {
                    expiryDate = parseGs1Date(rest.substring(2, 8));
                    rest = rest.substring(8);
                }
                if (rest.startsWith("10") && rest.length() > 2) {
                    batchNumber = rest.substring(2);
                }
            } else if (raw.matches("^\\d{12,14}$")) {
                gtin = padGtin14(raw);
            } else {
                throw new InvalidBarcodeException("Unrecognized barcode format. Expected GS1-128 format e.g. (01)06161101234567(10)BAT-123(17)270630");
            }
        }

        if (gtin == null) {
            throw new InvalidBarcodeException("GS1 barcode must contain a valid GTIN under AI (01)");
        }

        boolean validCheckDigit = validateGtinCheckDigit(gtin);
        if (!validCheckDigit) {
            throw new InvalidBarcodeException("Invalid GTIN check digit for barcode GTIN: " + gtin);
        }

        return ParsedGs1Data.builder()
                .gtin(gtin)
                .batchNumber(batchNumber != null ? batchNumber : "DEFAULT-BATCH")
                .expiryDate(expiryDate)
                .serialNumber(serialNumber)
                .rawBarcode(raw)
                .checkDigitValid(true)
                .build();
    }

    /**
     * Validates GTIN check digit using standard GS1 Modulo-10 algorithm.
     */
    public boolean validateGtinCheckDigit(String gtin) {
        if (gtin == null || !gtin.matches("^\\d{8,14}$")) {
            return false;
        }

        int length = gtin.length();
        int expectedCheckDigit = Character.getNumericValue(gtin.charAt(length - 1));

        int sum = 0;
        boolean multiplyByThree = true;

        for (int i = length - 2; i >= 0; i--) {
            int digit = Character.getNumericValue(gtin.charAt(i));
            sum += (multiplyByThree ? digit * 3 : digit);
            multiplyByThree = !multiplyByThree;
        }

        int calculatedCheckDigit = (10 - (sum % 10)) % 10;
        return calculatedCheckDigit == expectedCheckDigit;
    }

    public static String calculateGtinCheckDigit(String numberWithoutCheckDigit) {
        int length = numberWithoutCheckDigit.length();
        int sum = 0;
        boolean multiplyByThree = true;

        for (int i = length - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(numberWithoutCheckDigit.charAt(i));
            sum += (multiplyByThree ? digit * 3 : digit);
            multiplyByThree = !multiplyByThree;
        }

        int calculatedCheckDigit = (10 - (sum % 10)) % 10;
        return String.valueOf(calculatedCheckDigit);
    }

    /**
     * Simulates scanning a barcode: parses GS1, validates product, and performs IN (RECEIPT) or OUT (ISSUE).
     */
    @Transactional
    public BarcodeScanResponse scanBarcode(BarcodeScanRequest request, String userId) {
        ParsedGs1Data parsed = parseGs1Barcode(request.getBarcode());

        // Locate product by barcode or SKU
        Product product = productRepository.findByBarcode(parsed.getGtin())
                .or(() -> productRepository.findByBarcode(parsed.getRawBarcode()))
                .or(() -> productRepository.findBySku(parsed.getGtin()))
                .orElseThrow(() -> new ResourceNotFoundException("No registered product found for scanned barcode GTIN: " + parsed.getGtin()));

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Scanned location ID not found: " + request.getLocationId()));

        int qty = (request.getQuantity() != null && request.getQuantity() > 0) ? request.getQuantity() : 1;
        Long movementId = null;

        if ("IN".equalsIgnoreCase(request.getAction())) {
            ReceiveStockRequest receiveRequest = ReceiveStockRequest.builder()
                    .productId(product.getId())
                    .locationId(location.getId())
                    .quantity(qty)
                    .batchNumber(parsed.getBatchNumber())
                    .expiryDate(parsed.getExpiryDate())
                    .reference("SCAN-IN-" + (parsed.getSerialNumber() != null ? parsed.getSerialNumber() : System.currentTimeMillis()))
                    .build();
            StockMovementResponse mov = stockService.receiveStock(receiveRequest, userId);
            movementId = mov.getId();
        } else if ("OUT".equalsIgnoreCase(request.getAction())) {
            IssueStockRequest issueRequest = IssueStockRequest.builder()
                    .productId(product.getId())
                    .locationId(location.getId())
                    .quantity(qty)
                    .batchNumber(parsed.getBatchNumber())
                    .reference("SCAN-OUT-" + (parsed.getSerialNumber() != null ? parsed.getSerialNumber() : System.currentTimeMillis()))
                    .build();
            StockMovementResponse mov = stockService.issueStock(issueRequest, userId);
            movementId = mov.getId();
        } else {
            throw new InvalidBarcodeException("Invalid scan action: " + request.getAction() + ". Must be 'IN' or 'OUT'.");
        }

        log.info("Barcode scan simulation successful: Action={}, GTIN={}, SKU={}, Location={}",
                request.getAction(), parsed.getGtin(), product.getSku(), location.getCode());

        return BarcodeScanResponse.builder()
                .action(request.getAction().toUpperCase())
                .gtin(parsed.getGtin())
                .sku(product.getSku())
                .productName(product.getName())
                .batchNumber(parsed.getBatchNumber())
                .expiryDate(parsed.getExpiryDate())
                .serialNumber(parsed.getSerialNumber())
                .locationId(location.getId())
                .locationCode(location.getCode())
                .quantityMoved(qty)
                .status("SUCCESS")
                .message("Successfully processed " + request.getAction() + " scan for " + product.getName())
                .movementId(movementId)
                .build();
    }

    /**
     * Generates a GS1-128 barcode image (PNG format) using ZXing.
     */
    public byte[] generateBarcodeImage(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        String gtin = product.getBarcode();
        if (gtin == null || gtin.isBlank()) {
            gtin = product.getSku();
        }

        // Format GS1 string e.g. (01)06161101234567(10)BAT-2026(17)271231
        String gs1Content = String.format("(01)%s(10)BAT-%s(17)271231", padGtin14(gtin), product.getId());

        try {
            Code128Writer writer = new Code128Writer();
            BitMatrix bitMatrix = writer.encode(gs1Content, BarcodeFormat.CODE_128, 400, 150);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (Exception ex) {
            log.error("Failed to generate barcode image for product {}: {}", productId, ex.getMessage());
            throw new RuntimeException("Could not generate barcode image", ex);
        }
    }

    private String padGtin14(String gtin) {
        String clean = gtin.replaceAll("\\D", "");
        if (clean.length() < 14) {
            return String.format("%14s", clean).replace(' ', '0');
        }
        return clean;
    }

    private LocalDate parseGs1Date(String yymmdd) {
        try {
            if (yymmdd.length() != 6) return null;
            // YYMMDD (20YY-MM-DD)
            int year = 2000 + Integer.parseInt(yymmdd.substring(0, 2));
            int month = Integer.parseInt(yymmdd.substring(2, 4));
            int day = Integer.parseInt(yymmdd.substring(4, 6));
            if (day == 0) day = 1; // End of month convention in GS1 when DD=00
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            log.warn("Could not parse GS1 expiry date from string: {}", yymmdd);
            return null;
        }
    }
}
