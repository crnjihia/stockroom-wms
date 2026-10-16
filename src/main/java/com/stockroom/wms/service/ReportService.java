package com.stockroom.wms.service;

import com.stockroom.wms.config.WmsMetrics;
import com.stockroom.wms.domain.*;
import com.stockroom.wms.dto.report.ReportDTOs.*;
import com.stockroom.wms.exception.ResourceNotFoundException;
import com.stockroom.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class ReportService {

    private final StockItemRepository stockItemRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseRepository warehouseRepository;
    private final ReportJobRepository reportJobRepository;
    private final WmsMetrics wmsMetrics;

    public ReportService(
            StockItemRepository stockItemRepository,
            ProductRepository productRepository,
            StockMovementRepository stockMovementRepository,
            WarehouseRepository warehouseRepository,
            ReportJobRepository reportJobRepository,
            WmsMetrics wmsMetrics
    ) {
        this.stockItemRepository = stockItemRepository;
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.warehouseRepository = warehouseRepository;
        this.reportJobRepository = reportJobRepository;
        this.wmsMetrics = wmsMetrics;
    }

    @Value("${app.reports.storage-dir:./target/reports}")
    private String storageDir;

    // --- Synchronous Reports ---

    @Transactional(readOnly = true)
    public byte[] generateInventoryPdf(Long warehouseId) {
        Instant start = Instant.now();
        List<StockItem> items = (warehouseId != null)
                ? stockItemRepository.findByLocationWarehouseId(warehouseId)
                : stockItemRepository.findAll();

        String whName = "All Depots";
        if (warehouseId != null) {
            whName = warehouseRepository.findById(warehouseId)
                    .map(Warehouse::getName).orElse("Depot #" + warehouseId);
        }

        List<Map<String, Object>> data = items.stream().map(item -> {
            Map<String, Object> map = new HashMap<>();
            map.put("warehouseCode", item.getLocation().getWarehouse().getCode());
            map.put("locationCode", item.getLocation().getCode());
            map.put("sku", item.getProduct().getSku());
            map.put("productName", item.getProduct().getName());
            map.put("batchNumber", item.getBatchNumber());
            map.put("quantityOnHand", item.getQuantityOnHand());
            map.put("quantityReserved", item.getQuantityReserved());
            map.put("quantityAvailable", item.getQuantityAvailable());
            return map;
        }).toList();

        Map<String, Object> params = new HashMap<>();
        params.put("reportTitle", "Current Inventory Valuation & Stock Summary");
        params.put("generatedBy", "Stockroom Automated System");
        params.put("warehouseFilter", whName);

        byte[] pdfBytes = renderJasperPdf("/reports/inventory_report.jrxml", params, data);
        wmsMetrics.recordReportDuration(ReportType.INVENTORY_PDF, "PDF", Duration.between(start, Instant.now()));
        return pdfBytes;
    }

    @Transactional(readOnly = true)
    public byte[] generateLowStockPdf() {
        Instant start = Instant.now();
        List<Product> products = productRepository.findAll();
        List<Map<String, Object>> data = new ArrayList<>();

        for (Product product : products) {
            int totalOnHand = stockItemRepository.sumOnHandByProductId(product.getId());
            if (totalOnHand <= product.getReorderPoint()) {
                Map<String, Object> map = new HashMap<>();
                map.put("sku", product.getSku());
                map.put("productName", product.getName());
                map.put("category", product.getCategory());
                map.put("currentQty", totalOnHand);
                map.put("reorderPoint", product.getReorderPoint());
                map.put("suggestedQty", product.getReorderQty());
                map.put("warehouseCode", "ALL");
                data.add(map);
            }
        }

        Map<String, Object> params = new HashMap<>();
        params.put("reportTitle", "Stockroom WMS - Low Stock Reorder Triggers");
        params.put("generatedBy", "Stockroom Automated Auditor");

        byte[] pdfBytes = renderJasperPdf("/reports/low_stock_report.jrxml", params, data);
        wmsMetrics.recordReportDuration(ReportType.LOW_STOCK_PDF, "PDF", Duration.between(start, Instant.now()));
        return pdfBytes;
    }

    @Transactional(readOnly = true)
    public byte[] generateInventoryXlsx(Long warehouseId) {
        Instant start = Instant.now();
        List<StockItem> items = (warehouseId != null)
                ? stockItemRepository.findByLocationWarehouseId(warehouseId)
                : stockItemRepository.findAll();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Inventory");
            sheet.setDisplayGridlines(true);

            CellStyle headerStyle = createHeaderStyle(workbook, IndexedColors.ROYAL_BLUE.getIndex());
            CellStyle numStyle = workbook.createCellStyle();
            numStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));

            CellStyle currStyle = workbook.createCellStyle();
            currStyle.setDataFormat(workbook.createDataFormat().getFormat("KES #,##0.00"));

            String[] headers = {
                    "Depot Code", "Depot Name", "Location", "Zone", "SKU", "Product Name",
                    "Category", "Batch", "Expiry Date", "On Hand", "Reserved", "Available",
                    "Unit Cost (KES)", "Total Value (KES)"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (StockItem item : items) {
                Row row = sheet.createRow(rowIndex);
                row.createCell(0).setCellValue(item.getLocation().getWarehouse().getCode());
                row.createCell(1).setCellValue(item.getLocation().getWarehouse().getName());
                row.createCell(2).setCellValue(item.getLocation().getCode());
                row.createCell(3).setCellValue(item.getLocation().getZone().name());
                row.createCell(4).setCellValue(item.getProduct().getSku());
                row.createCell(5).setCellValue(item.getProduct().getName());
                row.createCell(6).setCellValue(item.getProduct().getCategory());
                row.createCell(7).setCellValue(item.getBatchNumber());
                row.createCell(8).setCellValue(item.getExpiryDate() != null ? item.getExpiryDate().toString() : "N/A");

                Cell cOnHand = row.createCell(9);
                cOnHand.setCellValue(item.getQuantityOnHand());
                cOnHand.setCellStyle(numStyle);

                Cell cRes = row.createCell(10);
                cRes.setCellValue(item.getQuantityReserved());
                cRes.setCellStyle(numStyle);

                Cell cAvail = row.createCell(11);
                cAvail.setCellValue(item.getQuantityAvailable());
                cAvail.setCellStyle(numStyle);

                Cell cCost = row.createCell(12);
                cCost.setCellValue(item.getProduct().getUnitCost().doubleValue());
                cCost.setCellStyle(currStyle);

                // Formula: J{row} * M{row} (On Hand * Unit Cost)
                int rNum = rowIndex + 1;
                Cell cVal = row.createCell(13);
                cVal.setCellFormula("J" + rNum + "*M" + rNum);
                cVal.setCellStyle(currStyle);

                rowIndex++;
            }

            // Summary Totals Row
            if (rowIndex > 1) {
                Row totRow = sheet.createRow(rowIndex);
                CellStyle boldStyle = workbook.createCellStyle();
                Font f = workbook.createFont();
                f.setBold(true);
                boldStyle.setFont(f);

                Cell cLabel = totRow.createCell(5);
                cLabel.setCellValue("TOTALS:");
                cLabel.setCellStyle(boldStyle);

                Cell cTotOnHand = totRow.createCell(9);
                cTotOnHand.setCellFormula("SUM(J2:J" + rowIndex + ")");
                cTotOnHand.setCellStyle(boldStyle);

                Cell cTotVal = totRow.createCell(13);
                cTotVal.setCellFormula("SUM(N2:N" + rowIndex + ")");
                cTotVal.setCellStyle(boldStyle);
            }

            // Auto-filter
            if (rowIndex > 1) {
                sheet.setAutoFilter(new CellRangeAddress(0, rowIndex - 1, 0, headers.length - 1));
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            wmsMetrics.recordReportDuration(ReportType.INVENTORY_XLSX, "XLSX", Duration.between(start, Instant.now()));
            return out.toByteArray();
        } catch (Exception ex) {
            log.error("Failed to generate inventory XLSX: {}", ex.getMessage(), ex);
            throw new RuntimeException("Could not generate inventory XLSX report", ex);
        }
    }

    @Transactional(readOnly = true)
    public byte[] generateMovementsXlsx(Instant from, Instant to) {
        Instant start = Instant.now();
        Specification<StockMovement> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<StockMovement> movements = stockMovementRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Stock Movements");
            sheet.setDisplayGridlines(true);

            CellStyle headerStyle = createHeaderStyle(workbook, IndexedColors.GREY_80_PERCENT.getIndex());
            CellStyle numStyle = workbook.createCellStyle();
            numStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));

            String[] headers = {
                    "Movement ID", "Timestamp (UTC)", "Depot", "Location", "SKU",
                    "Product Name", "Movement Type", "Quantity", "Reference Type",
                    "Reference ID", "User ID", "Notes"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (StockMovement m : movements) {
                Row row = sheet.createRow(rowIndex);
                row.createCell(0).setCellValue(m.getId());
                row.createCell(1).setCellValue(m.getCreatedAt() != null ? m.getCreatedAt().toString() : "");
                row.createCell(2).setCellValue(m.getLocation().getWarehouse().getCode());
                row.createCell(3).setCellValue(m.getLocation().getCode());
                row.createCell(4).setCellValue(m.getProduct().getSku());
                row.createCell(5).setCellValue(m.getProduct().getName());
                row.createCell(6).setCellValue(m.getType().name());

                Cell qCell = row.createCell(7);
                qCell.setCellValue(m.getQuantity());
                qCell.setCellStyle(numStyle);

                row.createCell(8).setCellValue(m.getReferenceType() != null ? m.getReferenceType() : "");
                row.createCell(9).setCellValue(m.getReferenceId() != null ? m.getReferenceId() : "");
                row.createCell(10).setCellValue(m.getUserId() != null ? m.getUserId() : "");
                row.createCell(11).setCellValue(m.getNotes() != null ? m.getNotes() : "");

                rowIndex++;
            }

            if (rowIndex > 1) {
                sheet.setAutoFilter(new CellRangeAddress(0, rowIndex - 1, 0, headers.length - 1));
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            wmsMetrics.recordReportDuration(ReportType.MOVEMENTS_XLSX, "XLSX", Duration.between(start, Instant.now()));
            return out.toByteArray();
        } catch (Exception ex) {
            log.error("Failed to generate movements XLSX: {}", ex.getMessage(), ex);
            throw new RuntimeException("Could not generate movements XLSX report", ex);
        }
    }

    @Transactional(readOnly = true)
    public byte[] generateValuationXlsx() {
        Instant start = Instant.now();
        List<StockItem> items = stockItemRepository.findAll();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("FIFO Valuation");
            sheet.setDisplayGridlines(true);

            CellStyle headerStyle = createHeaderStyle(workbook, IndexedColors.DARK_TEAL.getIndex());
            CellStyle numStyle = workbook.createCellStyle();
            numStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));

            CellStyle currStyle = workbook.createCellStyle();
            currStyle.setDataFormat(workbook.createDataFormat().getFormat("KES #,##0.00"));

            String[] headers = {
                    "Depot", "SKU", "Product Name", "Category", "Batch",
                    "Expiry Date", "Valuation Method", "Quantity", "Unit Cost (KES)", "Valuation (KES)"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Sort FIFO: expiry date asc, id asc
            items.sort(Comparator.comparing((StockItem i) -> i.getExpiryDate() != null ? i.getExpiryDate() : java.time.LocalDate.MAX)
                    .thenComparing(StockItem::getId));

            int rowIndex = 1;
            for (StockItem item : items) {
                if (item.getQuantityOnHand() <= 0) continue;

                Row row = sheet.createRow(rowIndex);
                row.createCell(0).setCellValue(item.getLocation().getWarehouse().getCode());
                row.createCell(1).setCellValue(item.getProduct().getSku());
                row.createCell(2).setCellValue(item.getProduct().getName());
                row.createCell(3).setCellValue(item.getProduct().getCategory());
                row.createCell(4).setCellValue(item.getBatchNumber());
                row.createCell(5).setCellValue(item.getExpiryDate() != null ? item.getExpiryDate().toString() : "N/A");
                row.createCell(6).setCellValue("FIFO (First-In, First-Out)");

                Cell qCell = row.createCell(7);
                qCell.setCellValue(item.getQuantityOnHand());
                qCell.setCellStyle(numStyle);

                Cell cCost = row.createCell(8);
                cCost.setCellValue(item.getProduct().getUnitCost().doubleValue());
                cCost.setCellStyle(currStyle);

                int rNum = rowIndex + 1;
                Cell cVal = row.createCell(9);
                cVal.setCellFormula("H" + rNum + "*I" + rNum);
                cVal.setCellStyle(currStyle);

                rowIndex++;
            }

            if (rowIndex > 1) {
                Row totRow = sheet.createRow(rowIndex);
                CellStyle boldStyle = workbook.createCellStyle();
                Font f = workbook.createFont();
                f.setBold(true);
                boldStyle.setFont(f);

                Cell cLabel = totRow.createCell(6);
                cLabel.setCellValue("TOTAL VALUATION:");
                cLabel.setCellStyle(boldStyle);

                Cell cTotQty = totRow.createCell(7);
                cTotQty.setCellFormula("SUM(H2:H" + rowIndex + ")");
                cTotQty.setCellStyle(boldStyle);

                Cell cTotVal = totRow.createCell(9);
                cTotVal.setCellFormula("SUM(J2:J" + rowIndex + ")");
                cTotVal.setCellStyle(boldStyle);

                sheet.setAutoFilter(new CellRangeAddress(0, rowIndex - 1, 0, headers.length - 1));
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            wmsMetrics.recordReportDuration(ReportType.VALUATION_XLSX, "XLSX", Duration.between(start, Instant.now()));
            return out.toByteArray();
        } catch (Exception ex) {
            log.error("Failed to generate valuation XLSX: {}", ex.getMessage(), ex);
            throw new RuntimeException("Could not generate FIFO valuation report", ex);
        }
    }

    // --- Asynchronous Reports ---

    @Transactional
    public ReportJobResponse queueAsyncReport(AsyncReportRequest request) {
        String jobId = UUID.randomUUID().toString();
        String format = request.getReportType().name().endsWith("PDF") ? "PDF" : "XLSX";

        ReportJob job = ReportJob.builder()
                .id(jobId)
                .reportType(request.getReportType())
                .format(format)
                .status(ReportStatus.QUEUED)
                .downloadUrl("/reports/download/" + jobId)
                .build();

        ReportJob saved = reportJobRepository.save(job);
        log.info("Queued async report job {} for type {}", jobId, request.getReportType());

        // Launch async generation task
        processAsyncReport(jobId, request);

        return mapToJobResponse(saved);
    }

    @Async
    public void processAsyncReport(String jobId, AsyncReportRequest request) {
        ReportJob job = reportJobRepository.findById(jobId).orElse(null);
        if (job == null) return;

        try {
            job.setStatus(ReportStatus.PROCESSING);
            reportJobRepository.save(job);

            byte[] content = switch (request.getReportType()) {
                case INVENTORY_PDF -> generateInventoryPdf(request.getWarehouseId());
                case INVENTORY_XLSX -> generateInventoryXlsx(request.getWarehouseId());
                case LOW_STOCK_PDF -> generateLowStockPdf();
                case MOVEMENTS_XLSX -> generateMovementsXlsx(request.getFrom(), request.getTo());
                case VALUATION_XLSX -> generateValuationXlsx();
            };

            // Write to local reports directory
            File dir = new File(storageDir);
            if (!dir.exists()) dir.mkdirs();

            String ext = job.getFormat().toLowerCase();
            String filename = "report_" + jobId + "." + ext;
            File targetFile = new File(dir, filename);
            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                fos.write(content);
            }

            job.setStatus(ReportStatus.COMPLETED);
            job.setFileSizeBytes((long) content.length);
            job.setCompletedAt(Instant.now());
            job.setDownloadUrl("/reports/download/" + jobId);
            reportJobRepository.save(job);

            log.info("Async report job {} completed successfully. Size: {} bytes", jobId, content.length);
        } catch (Exception ex) {
            log.error("Async report job {} failed: {}", jobId, ex.getMessage(), ex);
            job.setStatus(ReportStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
            job.setCompletedAt(Instant.now());
            reportJobRepository.save(job);
        }
    }

    @Transactional(readOnly = true)
    public ReportJobResponse getJobStatus(String jobId) {
        ReportJob job = reportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Report job not found: " + jobId));
        return mapToJobResponse(job);
    }

    public byte[] getReportFile(String jobId) {
        ReportJob job = reportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Report job not found: " + jobId));

        if (job.getStatus() != ReportStatus.COMPLETED) {
            throw new RuntimeException("Report job is not ready for download; current status: " + job.getStatus());
        }

        String ext = job.getFormat().toLowerCase();
        File file = new File(storageDir, "report_" + jobId + "." + ext);
        if (!file.exists()) {
            throw new ResourceNotFoundException("Report file on disk could not be found");
        }

        try {
            return Files.readAllBytes(file.toPath());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to read report file", ex);
        }
    }

    // --- Helpers ---

    private byte[] renderJasperPdf(String templateClasspath, Map<String, Object> parameters, List<?> data) {
        try {
            JasperReport jasperReport = null;
            String jasperPath = templateClasspath.replace(".jrxml", ".jasper");
            try {
                InputStream jasperStream = getClass().getResourceAsStream(jasperPath);
                if (jasperStream != null) {
                    try (jasperStream) {
                        jasperReport = (JasperReport) net.sf.jasperreports.engine.util.JRLoader.loadObject(jasperStream);
                    }
                }
            } catch (Throwable t) {
                log.warn("Could not deserialize precompiled jasper file {}, compiling from jrxml: {}", jasperPath, t.getMessage());
                jasperReport = null;
            }

            if (jasperReport == null) {
                try (InputStream stream = getClass().getResourceAsStream(templateClasspath)) {
                    if (stream == null) {
                        throw new IllegalStateException("Jasper template not found: " + templateClasspath);
                    }
                    jasperReport = JasperCompileManager.compileReport(stream);
                }
            }
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(data);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            return JasperExportManager.exportReportToPdf(jasperPrint);
        } catch (Exception ex) {
            log.error("JasperReports error: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to render PDF report: " + ex.getMessage(), ex);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook, short bgColor) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setFillForegroundColor(bgColor);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private ReportJobResponse mapToJobResponse(ReportJob job) {
        return ReportJobResponse.builder()
                .jobId(job.getId())
                .reportType(job.getReportType())
                .format(job.getFormat())
                .status(job.getStatus())
                .downloadUrl(job.getDownloadUrl())
                .errorMessage(job.getErrorMessage())
                .fileSizeBytes(job.getFileSizeBytes())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .build();
    }
}
