package com.marketsentinel.alert;

import com.marketsentinel.alert.AlertViews.AccountRisk;
import com.marketsentinel.alert.AlertViews.AlertDetail;
import com.marketsentinel.alert.AlertViews.AlertSummary;
import com.marketsentinel.alert.AlertViews.Bar;
import com.marketsentinel.alert.AlertViews.Edge;
import com.marketsentinel.alert.AlertViews.MarketContext;
import com.marketsentinel.alert.AlertViews.OrderPoint;
import com.marketsentinel.alert.AlertViews.PatternCount;
import com.marketsentinel.alert.AlertViews.Stats;
import com.marketsentinel.alert.AlertViews.TradePoint;
import com.marketsentinel.caseflow.CaseRepository;
import com.marketsentinel.caseflow.InvestigationCase;
import com.marketsentinel.config.SentinelProperties;
import com.marketsentinel.market.MarketOrder;
import com.marketsentinel.market.OrderRepository;
import com.marketsentinel.market.Trade;
import com.marketsentinel.market.TradeRepository;
import com.marketsentinel.web.ApiException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class AlertService {

    private final AlertRepository alerts;
    private final CaseRepository cases;
    private final TradeRepository trades;
    private final OrderRepository orders;
    private final ObjectMapper json;
    private final ZoneId zone;

    public AlertService(AlertRepository alerts, CaseRepository cases, TradeRepository trades, OrderRepository orders,
            ObjectMapper json, SentinelProperties props) {
        this.alerts = alerts;
        this.cases = cases;
        this.trades = trades;
        this.orders = orders;
        this.json = json;
        this.zone = props.detection().marketZone();
    }

    public Page<AlertSummary> search(UUID tenantId, AlertStatus status, Pattern pattern, String symbol, int page, int size) {
        return alerts.search(tenantId, status, pattern, symbol == null || symbol.isBlank() ? null : symbol.toUpperCase(),
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)))).map(AlertSummary::of);
    }

    public Alert get(UUID tenantId, Long id) {
        return alerts.findByIdAndTenantId(id, tenantId).orElseThrow(() -> ApiException.notFound("Alert " + id));
    }

    public AlertDetail detail(UUID tenantId, Long id) {
        Alert a = get(tenantId, id);
        Long caseId = cases.findByTenantIdAndAlertId(tenantId, id).map(InvestigationCase::getId).orElse(null);
        return new AlertDetail(AlertSummary.of(a), a.getReason(), json.readTree(a.getEvidence()), a.getWindowStart(),
                a.getWindowEnd(), caseId);
    }

    /** Price bars for the symbol's day, plus the flagged accounts' trades, orders and trading network. */
    public MarketContext marketContext(UUID tenantId, Long id) {
        Alert a = get(tenantId, id);
        Instant from = a.getTradeDate().atStartOfDay(zone).toInstant();
        Instant to = a.getTradeDate().plusDays(1).atStartOfDay(zone).toInstant();
        List<Trade> dayTrades = trades.findByTenantIdAndSymbolAndExecutedAtBetweenOrderByExecutedAt(tenantId, a.getSymbol(),
                from, to);
        Set<String> flagged = new HashSet<>(a.getAccountList());

        List<TradePoint> flaggedTrades = dayTrades.stream()
                .filter(t -> flagged.contains(t.getBuyAccountRef()) || flagged.contains(t.getSellAccountRef()))
                .limit(500)
                .map(t -> new TradePoint(t.getTradeRef(), t.getExecutedAt(), t.getPrice(), t.getQuantity(),
                        t.getBuyAccountRef(), t.getSellAccountRef()))
                .toList();
        List<OrderPoint> flaggedOrders = orders
                .findByTenantIdAndSymbolAndPlacedAtBetweenOrderByPlacedAt(tenantId, a.getSymbol(), from, to).stream()
                .filter(o -> flagged.contains(o.getAccountRef()))
                .filter(o -> o.getCancelledAt() != null || o.getQuantity() >= 10_000)
                .limit(200)
                .map(AlertService::orderPoint)
                .toList();

        // Trading network: edges between accounts that traded with a flagged account.
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, long[]> shares = new HashMap<>();
        for (Trade t : dayTrades) {
            if (!flagged.contains(t.getBuyAccountRef()) && !flagged.contains(t.getSellAccountRef())) {
                continue;
            }
            String key = t.getSellAccountRef() + "→" + t.getBuyAccountRef();
            counts.computeIfAbsent(key, k -> new int[1])[0]++;
            shares.computeIfAbsent(key, k -> new long[1])[0] += t.getQuantity();
        }
        List<Edge> edges = counts.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, int[]> e) -> shares.get(e.getKey())[0]).reversed())
                .limit(40)
                .map(e -> {
                    String[] ends = e.getKey().split("→");
                    return new Edge(ends[0], ends[1], e.getValue()[0], shares.get(e.getKey())[0]);
                })
                .toList();
        Set<String> nodes = new java.util.TreeSet<>(flagged);
        edges.forEach(e -> {
            nodes.add(e.from());
            nodes.add(e.to());
        });

        return new MarketContext(a.getSymbol(), a.getTradeDate(), minuteBars(dayTrades), flaggedTrades, flaggedOrders,
                List.copyOf(nodes), edges);
    }

    public Stats stats(UUID tenantId) {
        List<Alert> all = alerts.findByTenantId(tenantId);
        Map<Pattern, Long> byPattern = new TreeMap<>();
        Map<String, int[]> risk = new HashMap<>();
        for (Alert a : all) {
            byPattern.merge(a.getPattern(), 1L, Long::sum);
            if (a.getStatus() != AlertStatus.CLOSED) {
                for (String acc : a.getAccountList()) {
                    int[] r = risk.computeIfAbsent(acc, k -> new int[2]);
                    r[0]++;
                    r[1] += a.getScore();
                }
            }
        }
        List<PatternCount> patterns = Arrays.stream(Pattern.values())
                .map(p -> new PatternCount(p, p.label(), byPattern.getOrDefault(p, 0L))).toList();
        // Account risk: the sum of open alert scores, capped at 100.
        List<AccountRisk> riskiest = risk.entrySet().stream()
                .map(e -> new AccountRisk(e.getKey(), e.getValue()[0], Math.min(100, e.getValue()[1])))
                .sorted(Comparator.comparingInt(AccountRisk::riskScore).reversed().thenComparing(AccountRisk::account))
                .limit(10)
                .toList();
        return new Stats(alerts.countByTenantIdAndStatus(tenantId, AlertStatus.NEW),
                alerts.countByTenantIdAndStatus(tenantId, AlertStatus.IN_REVIEW),
                alerts.countByTenantIdAndStatus(tenantId, AlertStatus.CLOSED), patterns, riskiest,
                trades.countByTenantId(tenantId));
    }

    static List<Bar> minuteBars(List<Trade> dayTrades) {
        Map<Instant, List<Trade>> byMinute = new TreeMap<>();
        for (Trade t : dayTrades) {
            byMinute.computeIfAbsent(t.getExecutedAt().truncatedTo(ChronoUnit.MINUTES), k -> new ArrayList<>()).add(t);
        }
        List<Bar> bars = new ArrayList<>();
        byMinute.forEach((minute, list) -> bars.add(new Bar(minute, list.getFirst().getPrice(),
                list.stream().map(Trade::getPrice).max(Comparator.naturalOrder()).orElseThrow(),
                list.stream().map(Trade::getPrice).min(Comparator.naturalOrder()).orElseThrow(), list.getLast().getPrice(),
                list.stream().mapToLong(Trade::getQuantity).sum())));
        return bars;
    }

    private static OrderPoint orderPoint(MarketOrder o) {
        return new OrderPoint(o.getOrderRef(), o.getAccountRef(), o.getSide().name(), o.getQuantity(), o.getLimitPrice(),
                o.getPlacedAt(), o.getCancelledAt(), o.getStatus().name());
    }
}
