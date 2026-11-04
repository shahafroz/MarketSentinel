package com.marketsentinel.alert;

import com.marketsentinel.web.DecimalString;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Response bodies for the alert API. */
public final class AlertViews {

    private AlertViews() {
    }

    public record AlertSummary(Long id, Pattern pattern, String patternLabel, String symbol, List<String> accounts,
            int score, String title, AlertStatus status, LocalDate tradeDate, Instant createdAt) {

        public static AlertSummary of(Alert a) {
            return new AlertSummary(a.getId(), a.getPattern(), a.getPattern().label(), a.getSymbol(), a.getAccountList(),
                    a.getScore(), a.getTitle(), a.getStatus(), a.getTradeDate(), a.getCreatedAt());
        }
    }

    public record AlertDetail(AlertSummary alert, String reason, JsonNode evidence, Instant windowStart, Instant windowEnd,
            Long caseId) {
    }

    /** One-minute price bar: open, high, low, close and volume. */
    public record Bar(Instant time, @DecimalString BigDecimal open, @DecimalString BigDecimal high, @DecimalString BigDecimal low, @DecimalString BigDecimal close,
            long volume) {
    }

    public record TradePoint(String tradeRef, Instant time, @DecimalString BigDecimal price, long quantity, String buyer, String seller) {
    }

    public record OrderPoint(String orderRef, String account, String side, long quantity, @DecimalString BigDecimal price, Instant placedAt,
            Instant cancelledAt, String status) {
    }

    public record Edge(String from, String to, int trades, long shares) {
    }

    /** Everything the dashboard needs to draw an alert's charts. */
    public record MarketContext(String symbol, LocalDate date, List<Bar> bars, List<TradePoint> flaggedTrades,
            List<OrderPoint> flaggedOrders, List<String> nodes, List<Edge> edges) {
    }

    public record AccountRisk(String account, int openAlerts, int riskScore) {
    }

    public record Stats(long open, long inReview, long closed, List<PatternCount> byPattern, List<AccountRisk> riskiestAccounts,
            long tradesStored, List<DailyActivity> daily) {
    }

    /** One market day: trades stored, their total value (price × quantity) and the alerts raised, by pattern. */
    public record DailyActivity(LocalDate day, long trades, @DecimalString BigDecimal tradedValue, Map<Pattern, Long> alerts) {
    }

    public record PatternCount(Pattern pattern, String label, long count) {
    }
}
