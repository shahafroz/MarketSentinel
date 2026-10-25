package com.marketsentinel.simulator;

import com.marketsentinel.audit.AuditService;
import com.marketsentinel.config.SentinelProperties;
import com.marketsentinel.detection.DetectionService;
import com.marketsentinel.market.TradeRepository;
import com.marketsentinel.web.ApiException;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates a simulated trading day and saves it with JDBC batch inserts (thousands of rows in one
 * round trip each), then runs detection on it. Simulated data does not count towards plan limits.
 */
@Service
public class SimulatorService {

    private final MarketSimulator simulator;
    private final JdbcTemplate jdbc;
    private final TradeRepository trades;
    private final DetectionService detection;
    private final AuditService audit;
    private final ZoneId zone;

    public SimulatorService(JdbcTemplate jdbc, TradeRepository trades, DetectionService detection, AuditService audit,
            SentinelProperties props) {
        this.zone = props.detection().marketZone();
        this.simulator = new MarketSimulator(zone);
        this.jdbc = jdbc;
        this.trades = trades;
        this.detection = detection;
        this.audit = audit;
    }

    public record SimulationResult(LocalDate date, int accounts, int orders, int trades,
            List<MarketSimulator.Injected> injected, DetectionService.RunResult detection) {
    }

    @Transactional
    public SimulationResult simulate(UUID tenantId, String actor, LocalDate requested) {
        LocalDate date = requested != null ? requested : nextFreeTradingDay(tenantId);
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new IllegalArgumentException(date + " is a weekend; markets are closed");
        }
        if (alreadySimulated(tenantId, date)) {
            throw new ApiException(HttpStatus.CONFLICT, date + " was already simulated. Pick another date.");
        }
        MarketSimulator.Result result = simulator.generate(date, tenantId.getMostSignificantBits() ^ date.toEpochDay());
        SimulatedDay day = result.day();

        jdbc.batchUpdate("""
                INSERT INTO accounts (tenant_id, account_ref, owner_ref, account_type) VALUES (?, ?, ?, ?)
                ON CONFLICT (tenant_id, account_ref) DO NOTHING
                """, day.accounts.values().stream()
                .map(a -> new Object[] { tenantId, a.ref(), a.owner(), a.type().name() }).toList());

        jdbc.batchUpdate("""
                INSERT INTO orders (tenant_id, order_ref, account_ref, symbol, side, quantity, limit_price, status,
                                    filled_quantity, placed_at, cancelled_at, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, day.orders, 1000, (ps, o) -> {
            ps.setObject(1, tenantId);
            ps.setString(2, o.ref());
            ps.setString(3, o.account());
            ps.setString(4, o.symbol());
            ps.setString(5, o.side().name());
            ps.setLong(6, o.qty());
            ps.setBigDecimal(7, o.price());
            ps.setString(8, o.status().name());
            ps.setLong(9, o.filled());
            ps.setObject(10, OffsetDateTime.ofInstant(o.placedAt(), ZoneOffset.UTC));
            if (o.cancelledAt() == null) {
                ps.setNull(11, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                ps.setObject(11, OffsetDateTime.ofInstant(o.cancelledAt(), ZoneOffset.UTC));
            }
            ps.setString(12, o.ref());
        });

        jdbc.batchUpdate("""
                INSERT INTO trades (tenant_id, trade_ref, symbol, buy_account_ref, sell_account_ref, buy_order_ref,
                                    sell_order_ref, price, quantity, executed_at, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, day.trades, 1000, (ps, t) -> {
            ps.setObject(1, tenantId);
            ps.setString(2, t.ref());
            ps.setString(3, t.symbol());
            ps.setString(4, t.buyAccount());
            ps.setString(5, t.sellAccount());
            ps.setString(6, t.buyOrder());
            ps.setString(7, t.sellOrder());
            ps.setBigDecimal(8, t.price());
            ps.setLong(9, t.qty());
            ps.setObject(10, OffsetDateTime.ofInstant(t.executedAt(), ZoneOffset.UTC));
            ps.setString(11, t.ref());
        });

        audit.record(tenantId, actor, "MARKET_DAY_SIMULATED", "trading_day", date,
                day.trades.size() + " trades, " + day.orders.size() + " orders");
        DetectionService.RunResult run = detection.runForDay(tenantId, date);
        return new SimulationResult(date, day.accounts.size(), day.orders.size(), day.trades.size(), result.injected(), run);
    }

    private boolean alreadySimulated(UUID tenantId, LocalDate date) {
        return trades.existsByTenantIdAndTradeRef(tenantId, "SIM-" + date.format(DateTimeFormatter.BASIC_ISO_DATE) + "-T1");
    }

    /** The most recent weekday, going back from today, that has not been simulated yet. */
    private LocalDate nextFreeTradingDay(UUID tenantId) {
        LocalDate d = LocalDate.now(zone);
        for (int i = 0; i < 400; i++, d = d.minusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY
                    && !alreadySimulated(tenantId, d)) {
                return d;
            }
        }
        throw new ApiException(HttpStatus.CONFLICT, "No free trading day in the last 400 days");
    }
}
