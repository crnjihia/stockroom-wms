package com.stockroom.wms.controller;

import com.stockroom.wms.domain.ReportJob;
import com.stockroom.wms.domain.ReportStatus;
import com.stockroom.wms.dto.report.ReportDTOs.*;
import com.stockroom.wms.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/reports")
@Tag(name = "Reports & Analytics", description = "Endpoints for JasperReports PDF and Apache POI Excel inventory reporting")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "Export Inventory Report as PDF (JasperReports)")
    @GetMapping(value = "/inventory.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getInventoryPdf(@RequestParam(required = false) Long warehouseId) {
        byte[] pdfBytes = reportService.generateInventoryPdf(warehouseId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"inventory_report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @Operation(summary = "Export Inventory Report as XLSX (Apache POI with styling & formulas)")
    @GetMapping(value = "/inventory.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> getInventoryXlsx(@RequestParam(required = false) Long warehouseId) {
        byte[] xlsxBytes = reportService.generateInventoryXlsx(warehouseId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"inventory_report.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsxBytes);
    }

    @Operation(summary = "Export Low-Stock Reorder Triggers Report as PDF (JasperReports)")
    @GetMapping(value = "/low-stock.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getLowStockPdf() {
        byte[] pdfBytes = reportService.generateLowStockPdf();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"low_stock_report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @Operation(summary = "Export Stock Movements Ledger as XLSX (Apache POI)")
    @GetMapping(value = "/movements.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> getMovementsXlsx(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        byte[] xlsxBytes = reportService.generateMovementsXlsx(from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"stock_movements_report.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsxBytes);
    }

    @Operation(summary = "Export FIFO Inventory Valuation Report as XLSX")
    @GetMapping(value = "/valuation.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> getValuationXlsx() {
        byte[] xlsxBytes = reportService.generateValuationXlsx();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fifo_inventory_valuation.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsxBytes);
    }

    @Operation(summary = "Submit an asynchronous report generation job (returns job ID for polling)")
    @PostMapping("/async")
    public ResponseEntity<ReportJobResponse> submitAsyncReport(@RequestBody AsyncReportRequest request) {
        return ResponseEntity.accepted().body(reportService.queueAsyncReport(request));
    }

    @Operation(summary = "Poll status and progress of an asynchronous report generation job")
    @GetMapping("/jobs/{id}")
    public ResponseEntity<ReportJobResponse> getJobStatus(@PathVariable String id) {
        return ResponseEntity.ok(reportService.getJobStatus(id));
    }

    @Operation(summary = "Download generated report artifact for a completed job")
    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> downloadReport(@PathVariable String id) {
        ReportJobResponse status = reportService.getJobStatus(id);
        byte[] fileBytes = reportService.getReportFile(id);

        String contentType = "PDF".equalsIgnoreCase(status.getFormat())
                ? MediaType.APPLICATION_PDF_VALUE
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

        String ext = status.getFormat().toLowerCase();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"stockroom_report_" + id + "." + ext + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(fileBytes);
    }
}
