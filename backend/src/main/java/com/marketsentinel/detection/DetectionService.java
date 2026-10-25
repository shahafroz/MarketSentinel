package com.marketsentinel.detection;

import com.marketsentinel.alert.Alert;
import com.marketsentinel.alert.AlertRepository;
import com.marketsentinel.audit.AuditService;
import com.marketsentinel.config.SentinelProperties;
import com.marketsentinel.edgar.InsiderTransactionRepository;
import com.marketsentinel.edgar.MaterialEventRepository;
import com.marketsentinel.market.AccountRepository;
import com.marketsentinel.market.OrderRepository;
import com.marketsentinel.market.TradeRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads a trading day from the database, runs every detector on it, and saves new findings as
 * alerts. Safe to run again: each finding has a fingerprint, and existing fingerprints are skipped.
 */
@Service
public class DetectionService {

    private static final Logger log = LoggerFactory.getLogger(DetectionService.class);

    private final List<Detector> detectors;
    private final InsiderBeforeNewsDetector insiderDetector;
    private final TradeRepository trades;
    private final OrderRepository orders;
    private final AccountRepository accounts;
    private final AlertRepository alerts;
    private final InsiderTransactionRepository insiderTransactions;
    private final MaterialEventRepository materialEvents;
    private final AuditService audit;
    private final ObjectMapper json;
    private final ZoneId zone;

    public DetectionService(List<Detector> detectors, InsiderBeforeNewsDetector insiderDetector, TradeRepository trades,
            OrderRepository orders, AccountRepository accounts, AlertRepository alerts,
            InsiderTransactionRepository insiderTransactions, MaterialEventRepository materialEvents, AuditService audit,
            ObjectMapper json, SentinelProperties props) {
        this.detectors = detectors;
        this.insiderDetector = insiderDetector;
        this.trades = trades;
        this.orders = orders;
        this.accounts = accounts;
        this.alerts = alerts;
        this.insiderTransactions = insiderTransactions;
        this.materialEvents = materialEvents;
        this.audit = audit;
        this.json = json;
        this.zone = props.detection().marketZone();
    }

    public record RunResult(LocalDate date, int findings, int newAlerts) {
    }

    @Transactional
    public RunResult runForDay(UUID tenantId, LocalDate date) {
        MarketDay day = loadDay(tenantId, date);
        List<Finding> findings = new ArrayList<>();
        for (Detector detector : detectors) {
            try {
                findings.addAll(detector.detect(day));
            } catch (RuntimeException e) {
                // One faulty detector must not stop the others.
                log.error("Detector {} failed for tenant {} on {}", detector.pattern(), tenantId, date, e);
            }
        }
        int created = save(tenantId, findings);
        log.info("Detection for tenant {} on {}: {} findings, {} new alerts", tenantId, date, findings.size(), created);
        return new RunResult(date, findings.size(), created);
    }

    @Transactional
    public RunResult runInsiderScreen(UUID tenantId) {
        List<Finding> findings = insiderDetector.detect(insiderTransactions.findByTenantIdOrderByTransactionDateDesc(tenantId),
                materialEvents.findByTenantIdOrderByFiledOnDesc(tenantId));
        int created = save(tenantId, findings);
        return new RunResult(LocalDate.now(zone), findings.size(), created);
    }

    MarketDay loadDay(UUID tenantId, LocalDate date) {
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        Map<String, MarketDay.AccountInfo> accountInfo = accounts.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(a -> a.getAccountRef(),
                        a -> new MarketDay.AccountInfo(a.getOwnerRef(), a.getAccountType()), (a, b) -> a));
        return new MarketDay(date, zone,
                trades.findByTenantIdAndExecutedAtBetweenOrderByExecutedAt(tenantId, from, to),
                orders.findByTenantIdAndPlacedAtBetweenOrderByPlacedAt(tenantId, from, to),
                accountInfo);
    }

    private int save(UUID tenantId, List<Finding> findings) {
        int created = 0;
        Map<String, Finding> unique = findings.stream()
                .collect(Collectors.toMap(Finding::fingerprint, Function.identity(), (a, b) -> a));
        for (Finding f : unique.values()) {
            if (alerts.existsByTenantIdAndFingerprint(tenantId, f.fingerprint())) {
                continue;
            }
            Alert alert = alerts.save(new Alert(tenantId, f.pattern(), f.symbol(), f.accounts(), f.score(), f.title(),
                    f.reason(), json.writeValueAsString(f.evidence()), f.tradeDate(), f.windowStart(), f.windowEnd(),
                    f.fingerprint()));
            audit.record(tenantId, "system", "ALERT_CREATED", "alert", alert.getId(),
                    f.pattern() + " " + f.symbol() + " score " + f.score());
            created++;
        }
        return created;
    }
}
