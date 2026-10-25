package com.marketsentinel.alert;

import com.marketsentinel.alert.AlertViews.AlertDetail;
import com.marketsentinel.alert.AlertViews.AlertSummary;
import com.marketsentinel.alert.AlertViews.MarketContext;
import com.marketsentinel.alert.AlertViews.Stats;
import com.marketsentinel.detection.DetectionService;
import com.marketsentinel.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AlertController {

    private final AlertService alerts;
    private final DetectionService detection;

    public AlertController(AlertService alerts, DetectionService detection) {
        this.alerts = alerts;
        this.detection = detection;
    }

    public record PageView<T>(List<T> items, int page, int size, long total) {
    }

    @GetMapping("/alerts")
    public PageView<AlertSummary> list(@RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) Pattern pattern, @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        Page<AlertSummary> result = alerts.search(CurrentUser.get().tenantId(), status, pattern, symbol, page, size);
        return new PageView<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @GetMapping("/alerts/stats")
    public Stats stats() {
        return alerts.stats(CurrentUser.get().tenantId());
    }

    @GetMapping("/alerts/{id}")
    public AlertDetail detail(@PathVariable Long id) {
        return alerts.detail(CurrentUser.get().tenantId(), id);
    }

    @GetMapping("/alerts/{id}/market")
    public MarketContext market(@PathVariable Long id) {
        return alerts.marketContext(CurrentUser.get().tenantId(), id);
    }

    /** Re-runs every detector for one trading day. Safe to repeat: existing alerts are not duplicated. */
    @PostMapping("/detection/run")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'ANALYST')")
    public DetectionService.RunResult run(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return detection.runForDay(CurrentUser.get().tenantId(), date);
    }
}
