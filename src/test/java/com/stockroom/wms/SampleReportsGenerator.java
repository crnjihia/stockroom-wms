package com.stockroom.wms;

import com.stockroom.wms.domain.*;
import com.stockroom.wms.repository.*;
import com.stockroom.wms.service.ReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "admin", roles = {"ADMIN", "WAREHOUSE_MANAGER", "AUDITOR"})
public class SampleReportsGenerator {

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

    @Test
    @DisplayName("Generate Sample PDF and Excel reports into /samples directory")
    void generateSampleOutputs() throws Exception {
        reservationRepository.deleteAll();
        returnRepository.deleteAll();
        backorderRepository.deleteAll();
        stockMovementRepository.deleteAll();
        stockItemRepository.deleteAll();
        locationRepository.deleteAll();
        warehouseRepository.deleteAll();
        productRepository.deleteAll();

        // 1. Depots
        Warehouse wh1 = warehouseRepository.save(Warehouse.builder()
                .code("NBO-01")
                .name("Nairobi Central Industrial Area Depot")
                .county("Nairobi")
                .town("Industrial Area")
                .address("Commercial Street, Off Enterprise Road")
                .active(true)
                .build());

        Warehouse wh2 = warehouseRepository.save(Warehouse.builder()
                .code("NBO-02")
                .name("Embakasi Logistics Hub")
                .county("Nairobi")
                .town("Embakasi")
                .address("Airport North Road, Near ICD")
                .active(true)
                .build());

        // 2. Locations
        Location loc1 = locationRepository.save(Location.builder()
                .warehouse(wh1)
                .code("A-01-01")
                .zone(Zone.STORAGE)
                .capacityUnits(2500)
                .build());

        Location loc2 = locationRepository.save(Location.builder()
                .warehouse(wh1)
                .code("A-01-02")
                .zone(Zone.STORAGE)
                .capacityUnits(2500)
                .build());

        Location loc3 = locationRepository.save(Location.builder()
                .warehouse(wh2)
                .code("B-01-01")
                .zone(Zone.STORAGE)
                .capacityUnits(3000)
                .build());

        // 3. Products
        Product p1 = productRepository.save(Product.builder()
                .sku("PMB-MZ-2KG")
                .name("Pembe Maize Flour 2kg")
                .category("Dry Foods")
                .unitOfMeasure("BALE")
                .barcode("06161101234566")
                .reorderPoint(150)
                .reorderQty(500)
                .unitCost(BigDecimal.valueOf(195.00))
                .unitPrice(BigDecimal.valueOf(230.00))
                .build());

        Product p2 = productRepository.save(Product.builder()
                .sku("JGO-MZ-2KG")
                .name("Jogoo Maize Flour 2kg")
                .category("Dry Foods")
                .unitOfMeasure("BALE")
                .barcode("06161102345674")
                .reorderPoint(200)
                .reorderQty(600)
                .unitCost(BigDecimal.valueOf(205.00))
                .unitPrice(BigDecimal.valueOf(240.00))
                .build());

        Product p3 = productRepository.save(Product.builder()
                .sku("RNA-OIL-5L")
                .name("Rina Vegetable Cooking Oil 5L")
                .category("Edible Oils")
                .unitOfMeasure("JERRICAN")
                .barcode("06161103456782")
                .reorderPoint(80)
                .reorderQty(250)
                .unitCost(BigDecimal.valueOf(1150.00))
                .unitPrice(BigDecimal.valueOf(1320.00))
                .build());

        Product p4 = productRepository.save(Product.builder()
                .sku("KTP-TEA-100S")
                .name("Ketepa Chai Gold Tea Bags 100s")
                .category("Beverages")
                .unitOfMeasure("CARTON")
                .barcode("06161104567890")
                .reorderPoint(100)
                .reorderQty(300)
                .unitCost(BigDecimal.valueOf(310.00))
                .unitPrice(BigDecimal.valueOf(380.00))
                .build());

        // 4. Stock items
        stockItemRepository.save(StockItem.builder()
                .product(p1)
                .location(loc1)
                .quantityOnHand(450)
                .quantityReserved(50)
                .batchNumber("BAT-PMB-2026A")
                .expiryDate(LocalDate.of(2027, 6, 30))
                .build());

        stockItemRepository.save(StockItem.builder()
                .product(p2)
                .location(loc2)
                .quantityOnHand(320)
                .quantityReserved(20)
                .batchNumber("BAT-JGO-2026A")
                .expiryDate(LocalDate.of(2027, 7, 15))
                .build());

        stockItemRepository.save(StockItem.builder()
                .product(p3)
                .location(loc1)
                .quantityOnHand(180)
                .quantityReserved(0)
                .batchNumber("BAT-RNA-2026B")
                .expiryDate(LocalDate.of(2027, 12, 31))
                .build());

        // Low stock item
        stockItemRepository.save(StockItem.builder()
                .product(p4)
                .location(loc3)
                .quantityOnHand(45)
                .quantityReserved(0)
                .batchNumber("BAT-KTP-2026A")
                .expiryDate(LocalDate.of(2027, 9, 30))
                .build());

        // 5. Movements
        stockMovementRepository.save(StockMovement.builder()
                .product(p1)
                .location(loc1)
                .type(MovementType.RECEIPT)
                .quantity(450)
                .referenceType("PURCHASE_RECEIPT")
                .referenceId("PO-NBO-001")
                .userId("admin")
                .notes("Intake shipment from Pembe Millers")
                .build());

        stockMovementRepository.save(StockMovement.builder()
                .product(p2)
                .location(loc2)
                .type(MovementType.RECEIPT)
                .quantity(320)
                .referenceType("PURCHASE_RECEIPT")
                .referenceId("PO-NBO-002")
                .userId("admin")
                .notes("Intake shipment from Unga Limited")
                .build());

        stockMovementRepository.save(StockMovement.builder()
                .product(p3)
                .location(loc1)
                .type(MovementType.RECEIPT)
                .quantity(180)
                .referenceType("PURCHASE_RECEIPT")
                .referenceId("PO-NBO-003")
                .userId("admin")
                .notes("Intake shipment from Kapa Oil Refineries")
                .build());

        stockMovementRepository.save(StockMovement.builder()
                .product(p4)
                .location(loc3)
                .type(MovementType.RECEIPT)
                .quantity(45)
                .referenceType("PURCHASE_RECEIPT")
                .referenceId("PO-NBO-004")
                .userId("admin")
                .notes("Intake shipment from Ketepa Tea Factory")
                .build());

        // Ensure samples directory exists
        File samplesDir = new File("samples");
        if (!samplesDir.exists()) {
            samplesDir.mkdirs();
        }

        // Generate files
        byte[] invPdf = reportService.generateInventoryPdf(null);
        writeToFile(new File(samplesDir, "inventory_report.pdf"), invPdf);

        byte[] invXlsx = reportService.generateInventoryXlsx(null);
        writeToFile(new File(samplesDir, "inventory_report.xlsx"), invXlsx);

        byte[] lowStockPdf = reportService.generateLowStockPdf();
        writeToFile(new File(samplesDir, "low_stock_report.pdf"), lowStockPdf);

        byte[] movXlsx = reportService.generateMovementsXlsx(null, null);
        writeToFile(new File(samplesDir, "movements_report.xlsx"), movXlsx);

        byte[] valXlsx = reportService.generateValuationXlsx();
        writeToFile(new File(samplesDir, "valuation_report.xlsx"), valXlsx);

        // Also precompile to .jasper files for production binary distribution
        net.sf.jasperreports.engine.JasperCompileManager.compileReportToFile("src/main/resources/reports/inventory_report.jrxml", "src/main/resources/reports/inventory_report.jasper");
        net.sf.jasperreports.engine.JasperCompileManager.compileReportToFile("src/main/resources/reports/low_stock_report.jrxml", "src/main/resources/reports/low_stock_report.jasper");

        assertTrue(new File(samplesDir, "inventory_report.pdf").exists());
        assertTrue(new File(samplesDir, "inventory_report.xlsx").exists());
        assertTrue(new File(samplesDir, "low_stock_report.pdf").exists());
        assertTrue(new File(samplesDir, "movements_report.xlsx").exists());
        assertTrue(new File(samplesDir, "valuation_report.xlsx").exists());
    }

    private void writeToFile(File file, byte[] data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
    }
}
