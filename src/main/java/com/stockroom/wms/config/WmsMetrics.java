package com.stockroom.wms.config;

import com.stockroom.wms.domain.MovementType;
import com.stockroom.wms.domain.ReportType;
import com.stockroom.wms.repository.ReorderAlertRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class WmsMetrics {

    private final MeterRegistry meterRegistry;
    private final ReorderAlertRepository alertRepository;

    public WmsMetrics(MeterRegistry meterRegistry, ReorderAlertRepository alertRepository) {
        this.meterRegistry = meterRegistry;
        this.alertRepository = alertRepository;
    }

    private final ConcurrentMap<MovementType, Counter> movementCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> reservationFailureCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> reportTimers = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // Active low-stock alerts gauge
        Gauge.builder("stock_reorder_alerts_active", alertRepository, ReorderAlertRepository::countOpenAlerts)
                .description("Number of currently active/open low-stock alerts")
                .register(meterRegistry);
    }

    public void incrementStockMovement(MovementType type) {
        movementCounters.computeIfAbsent(type, t ->
                Counter.builder("stock_movements_total")
                        .tag("type", t.name())
                        .description("Total stock movements recorded across all warehouses")
                        .register(meterRegistry)
        ).increment();
    }

    public void recordReservationFailure(String reason) {
        reservationFailureCounters.computeIfAbsent(reason, r ->
                Counter.builder("stock_reservation_failures_total")
                        .tag("reason", r)
                        .description("Number of failed stock reservation attempts")
                        .register(meterRegistry)
        ).increment();
    }

    public void recordReportDuration(ReportType type, String format, Duration duration) {
        String key = type.name() + "_" + format;
        reportTimers.computeIfAbsent(key, k ->
                Timer.builder("stock_report_duration_seconds")
                        .tag("report_type", type.name())
                        .tag("format", format)
                        .description("Execution duration for inventory and movements report generation")
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry)
        ).record(duration);
    }
}
