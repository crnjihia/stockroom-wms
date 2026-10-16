package com.stockroom.wms;

import com.stockroom.wms.domain.*;
import com.stockroom.wms.repository.*;
import com.stockroom.wms.service.ReportService;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "admin", roles = {"ADMIN", "WAREHOUSE_MANAGER"})
public class ReportGenerationTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private StockReturnRepository returnRepository;

    @Autowired
    private BackorderRepository backorderRepository;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        returnRepository.deleteAll();
        backorderRepository.deleteAll();
        stockMovementRepository.deleteAll();
        stockItemRepository.deleteAll();
        locationRepository.deleteAll();
        warehouseRepository.deleteAll();
        productRepository.deleteAll();

        Warehouse wh = warehouseRepository.save(Warehouse.builder()
                .code("NBO-01")
                .name("Nairobi Central Depot")
                .county("Nairobi")
                .town("Industrial Area")
                .address("Commercial Street")
                .active(true)
                .build());

        Location loc = locationRepository.save(Location.builder()
                .warehouse(wh)
                .code("A-01-01")
                .zone(Zone.STORAGE)
                .capacityUnits(1000)
                .build());

        Product prod = productRepository.save(Product.builder()
                .sku("PMB-MZ-2KG")
                .name("Pembe Maize Flour 2kg")
                .category("Dry Foods")
                .unitOfMeasure("BALE")
                .barcode("06161101234566")
                .reorderPoint(50)
                .reorderQty(200)
                .unitCost(BigDecimal.valueOf(195.00))
                .unitPrice(BigDecimal.valueOf(230.00))
                .build());

        stockItemRepository.save(StockItem.builder()
                .product(prod)
                .location(loc)
                .quantityOnHand(100)
                .quantityReserved(20)
                .batchNumber("BAT-PMB-TEST")
                .expiryDate(LocalDate.of(2027, 6, 30))
                .build());

        stockMovementRepository.save(StockMovement.builder()
                .product(prod)
                .location(loc)
                .type(MovementType.RECEIPT)
                .quantity(100)
                .referenceType("INITIAL_PO")
                .referenceId("PO-001")
                .userId("admin")
                .notes("Initial load")
                .build());
    }

    @Test
    @DisplayName("Reports: Generate Inventory PDF via JasperReports")
    void testGenerateInventoryPdf() {
        byte[] pdfBytes = reportService.generateInventoryPdf(null);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500, "PDF size should be substantial");
        String header = new String(pdfBytes, 0, 5);
        assertEquals("%PDF-", header, "Should start with standard PDF magic header");
    }

    @Test
    @DisplayName("Reports: Generate Low-Stock Reorder Triggers PDF via JasperReports")
    void testGenerateLowStockPdf() {
        byte[] pdfBytes = reportService.generateLowStockPdf();

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500);
        String header = new String(pdfBytes, 0, 5);
        assertEquals("%PDF-", header);
    }

    @Test
    @DisplayName("Reports: Generate Inventory XLSX via Apache POI")
    void testGenerateInventoryXlsx() throws IOException {
        byte[] xlsxBytes = reportService.generateInventoryXlsx(null);

        assertNotNull(xlsxBytes);
        assertTrue(xlsxBytes.length > 1000);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = workbook.getSheet("Inventory");
            assertNotNull(sheet);
            assertEquals("Depot Code", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("NBO-01", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("PMB-MZ-2KG", sheet.getRow(1).getCell(4).getStringCellValue());
        }
    }

    @Test
    @DisplayName("Reports: Generate Stock Movements XLSX via Apache POI")
    void testGenerateMovementsXlsx() throws IOException {
        byte[] xlsxBytes = reportService.generateMovementsXlsx(null, null);

        assertNotNull(xlsxBytes);
        assertTrue(xlsxBytes.length > 1000);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = workbook.getSheet("Stock Movements");
            assertNotNull(sheet);
            assertEquals("Movement ID", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("RECEIPT", sheet.getRow(1).getCell(6).getStringCellValue());
        }
    }

    @Test
    @DisplayName("Reports: Generate FIFO Inventory Valuation XLSX via Apache POI")
    void testGenerateValuationXlsx() throws IOException {
        byte[] xlsxBytes = reportService.generateValuationXlsx();

        assertNotNull(xlsxBytes);
        assertTrue(xlsxBytes.length > 1000);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = workbook.getSheet("FIFO Valuation");
            assertNotNull(sheet);
            assertEquals("FIFO (First-In, First-Out)", sheet.getRow(1).getCell(6).getStringCellValue());
        }
    }
}
