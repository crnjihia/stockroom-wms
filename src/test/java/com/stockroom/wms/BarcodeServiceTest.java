package com.stockroom.wms;

import com.stockroom.wms.dto.barcode.BarcodeDTOs.ParsedGs1Data;
import com.stockroom.wms.exception.InvalidBarcodeException;
import com.stockroom.wms.service.BarcodeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class BarcodeServiceTest {

    private final BarcodeService barcodeService = new BarcodeService(null, null, null);

    @Test
    @DisplayName("GS1-128: Parse valid barcode string with GTIN, Batch, Expiry, and Serial")
    void testParseValidGs1Barcode() {
        // GTIN: 06161101234566 (valid check digit 6), Batch: BAT-2026-X, Expiry: 27-06-30 (YYMMDD=270630), Serial: SN-998877
        String barcode = "(01)06161101234566(10)BAT-2026-X(17)270630(21)SN-998877";

        ParsedGs1Data data = barcodeService.parseGs1Barcode(barcode);

        assertNotNull(data);
        assertEquals("06161101234566", data.getGtin());
        assertEquals("BAT-2026-X", data.getBatchNumber());
        assertEquals(LocalDate.of(2027, 6, 30), data.getExpiryDate());
        assertEquals("SN-998877", data.getSerialNumber());
        assertTrue(data.isCheckDigitValid());
    }

    @Test
    @DisplayName("GS1-128: Validate GTIN Modulo-10 check digit algorithm")
    void testValidateGtinCheckDigit() {
        // Pembe Maize Flour GTIN-14: 06161101234566 (Valid check digit = 6)
        assertTrue(barcodeService.validateGtinCheckDigit("06161101234566"));

        // Jogoo Maize Flour GTIN-14: 06161102345674 (Valid check digit = 4)
        assertTrue(barcodeService.validateGtinCheckDigit("06161102345674"));

        // Ketepa Tea GTIN-14: 06161104567890 (Valid check digit = 0)
        assertTrue(barcodeService.validateGtinCheckDigit("06161104567890"));

        // Invalid Check Digit (changed last digit to 9)
        assertFalse(barcodeService.validateGtinCheckDigit("06161101234569"));
    }

    @Test
    @DisplayName("GS1-128: Reject invalid GTIN check digit during barcode parsing")
    void testRejectInvalidGtinCheckDigitInBarcode() {
        String invalidBarcode = "(01)06161101234569(10)BAT-FAIL(17)270630";

        InvalidBarcodeException ex = assertThrows(InvalidBarcodeException.class, () ->
                barcodeService.parseGs1Barcode(invalidBarcode)
        );

        assertTrue(ex.getMessage().contains("Invalid GTIN check digit"));
    }

    @Test
    @DisplayName("GS1-128: Reject empty or malformed barcode strings")
    void testRejectMalformedBarcode() {
        assertThrows(InvalidBarcodeException.class, () -> barcodeService.parseGs1Barcode(""));
        assertThrows(InvalidBarcodeException.class, () -> barcodeService.parseGs1Barcode(null));
        assertThrows(InvalidBarcodeException.class, () -> barcodeService.parseGs1Barcode("INVALID_NOT_GS1"));
    }
}
