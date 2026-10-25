package com.marketsentinel.market;

import com.marketsentinel.billing.UsageService;
import com.marketsentinel.config.SentinelProperties;
import com.marketsentinel.market.IngestRequests.AccountRequest;
import com.marketsentinel.market.IngestRequests.IngestResult;
import com.marketsentinel.market.IngestRequests.OrderRequest;
import com.marketsentinel.market.IngestRequests.OrderView;
import com.marketsentinel.market.IngestRequests.TradeRequest;
import com.marketsentinel.market.IngestRequests.TradeView;
import com.marketsentinel.pipeline.TradeEventPublisher;
import com.marketsentinel.web.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores orders and trades sent by a broker.
 *
 * Idempotency: every write carries an Idempotency-Key. Sending the same key again (a network retry)
 * returns the stored result instead of storing a duplicate. The database enforces it too, with a
 * UNIQUE (tenant_id, idempotency_key) constraint, so even two simultaneous retries cannot both win.
 */
@Service
public class IngestService {

    private final AccountRepository accounts;
    private final OrderRepository orders;
    private final TradeRepository trades;
    private final UsageService usage;
    private final TradeEventPublisher events;
    private final ZoneId zone;

    public IngestService(AccountRepository accounts, OrderRepository orders, TradeRepository trades, UsageService usage,
            TradeEventPublisher events, SentinelProperties props) {
        this.accounts = accounts;
        this.orders = orders;
        this.trades = trades;
        this.usage = usage;
        this.events = events;
        this.zone = props.detection().marketZone();
    }

    @Transactional
    public int upsertAccounts(UUID tenantId, List<AccountRequest> requests) {
        for (AccountRequest r : requests) {
            accounts.findByTenantIdAndAccountRef(tenantId, r.accountRef()).ifPresentOrElse(
                    a -> a.update(r.ownerRef(), r.accountType()),
                    () -> accounts.save(new Account(tenantId, r.accountRef(), r.ownerRef(), r.accountType())));
        }
        return requests.size();
    }

    @Transactional
    public IngestResult<OrderView> placeOrder(UUID tenantId, String idempotencyKey, OrderRequest r) {
        Optional<MarketOrder> existing = orders.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (existing.isPresent()) {
            requireSame(existing.get().getOrderRef(), r.orderRef());
            return new IngestResult<>(OrderView.of(existing.get()), true);
        }
        if (orders.findByTenantIdAndOrderRef(tenantId, r.orderRef()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Order " + r.orderRef() + " already exists");
        }
        MarketOrder order = orders.saveAndFlush(new MarketOrder(tenantId, r.orderRef(), r.accountRef(), r.symbol(),
                r.side(), r.quantity(), r.limitPrice(), r.placedAt(), idempotencyKey));
        events.dayChanged(tenantId, tradingDate(r.placedAt()));
        return new IngestResult<>(OrderView.of(order), false);
    }

    /** Cancelling is naturally idempotent: cancelling an already-cancelled order changes nothing. */
    @Transactional
    public OrderView cancelOrder(UUID tenantId, String orderRef, Instant cancelledAt) {
        MarketOrder order = orders.findByTenantIdAndOrderRef(tenantId, orderRef)
                .orElseThrow(() -> ApiException.notFound("Order " + orderRef));
        if (cancelledAt.isBefore(order.getPlacedAt())) {
            throw new IllegalArgumentException("cancelledAt is before the order was placed");
        }
        order.cancel(cancelledAt);
        events.dayChanged(tenantId, tradingDate(order.getPlacedAt()));
        return OrderView.of(order);
    }

    @Transactional
    public IngestResult<TradeView> recordTrade(UUID tenantId, String idempotencyKey, TradeRequest r) {
        Optional<Trade> existing = trades.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (existing.isPresent()) {
            requireSame(existing.get().getTradeRef(), r.tradeRef());
            return new IngestResult<>(TradeView.of(existing.get()), true);
        }
        if (trades.existsByTenantIdAndTradeRef(tenantId, r.tradeRef())) {
            throw new ApiException(HttpStatus.CONFLICT, "Trade " + r.tradeRef() + " already exists");
        }
        usage.consumeTrades(tenantId, 1);
        Trade trade = trades.saveAndFlush(new Trade(tenantId, r.tradeRef(), r.symbol(), r.buyAccountRef(),
                r.sellAccountRef(), r.buyOrderRef(), r.sellOrderRef(), r.price(), r.quantity(), r.executedAt(),
                idempotencyKey));
        fill(tenantId, r.buyOrderRef(), r.quantity());
        fill(tenantId, r.sellOrderRef(), r.quantity());
        events.dayChanged(tenantId, tradingDate(r.executedAt()));
        return new IngestResult<>(TradeView.of(trade), false);
    }

    private void fill(UUID tenantId, String orderRef, long qty) {
        if (orderRef != null && !orderRef.isBlank()) {
            orders.findByTenantIdAndOrderRef(tenantId, orderRef).ifPresent(o -> o.recordFill(qty));
        }
    }

    private LocalDate tradingDate(Instant instant) {
        return instant.atZone(zone).toLocalDate();
    }

    /** The same key with a different payload is a client bug; refuse it instead of guessing. */
    private static void requireSame(String storedRef, String requestedRef) {
        if (!storedRef.equals(requestedRef)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Idempotency-Key was already used for a different request (" + storedRef + ")");
        }
    }
}
