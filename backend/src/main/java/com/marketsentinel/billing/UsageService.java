package com.marketsentinel.billing;

import com.marketsentinel.tenant.Plan;
import com.marketsentinel.tenant.TenantRepository;
import com.marketsentinel.web.ApiException;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Counts trades per firm per month and enforces the plan's limit. */
@Service
public class UsageService {

    private final JdbcTemplate jdbc;
    private final TenantRepository tenants;

    public UsageService(JdbcTemplate jdbc, TenantRepository tenants) {
        this.jdbc = jdbc;
        this.tenants = tenants;
    }

    public static String currentMonth() {
        return YearMonth.now(ZoneOffset.UTC).toString();
    }

    public long tradesThisMonth(UUID tenantId) {
        Long n = jdbc.query("SELECT trades FROM usage_counters WHERE tenant_id = ? AND month = ?",
                rs -> rs.next() ? rs.getLong(1) : 0L, tenantId, currentMonth());
        return n == null ? 0 : n;
    }

    /** Throws 402 Payment Required if `count` more trades would exceed the plan; otherwise counts them. */
    @Transactional
    public void consumeTrades(UUID tenantId, long count) {
        Plan plan = tenants.findById(tenantId).orElseThrow().getPlan();
        // Atomic upsert-and-increment: safe when several requests arrive at once.
        Long total = jdbc.queryForObject("""
                INSERT INTO usage_counters (tenant_id, month, trades) VALUES (?, ?, ?)
                ON CONFLICT (tenant_id, month) DO UPDATE SET trades = usage_counters.trades + EXCLUDED.trades
                RETURNING trades
                """, Long.class, tenantId, currentMonth(), count);
        if (total != null && total > plan.monthlyTradeLimit()) {
            // Rolls back the increment together with the rejected trades.
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "Monthly trade limit of the " + plan
                    + " plan reached (" + plan.monthlyTradeLimit() + " trades). Upgrade to continue.");
        }
    }
}
