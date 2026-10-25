package com.marketsentinel.market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An order placed by an account: an instruction to buy or sell up to `quantity` at `limitPrice`. */
@Entity
@Table(name = "orders")
public class MarketOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String orderRef;

    @Column(nullable = false)
    private String accountRef;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Side side;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal limitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.NEW;

    @Column(nullable = false)
    private long filledQuantity;

    @Column(nullable = false)
    private Instant placedAt;

    private Instant cancelledAt;

    @Column(nullable = false)
    private String idempotencyKey;

    protected MarketOrder() {
    }

    public MarketOrder(UUID tenantId, String orderRef, String accountRef, String symbol, Side side, long quantity,
            BigDecimal limitPrice, Instant placedAt, String idempotencyKey) {
        this.tenantId = tenantId;
        this.orderRef = orderRef;
        this.accountRef = accountRef;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.placedAt = placedAt;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getOrderRef() { return orderRef; }
    public String getAccountRef() { return accountRef; }
    public String getSymbol() { return symbol; }
    public Side getSide() { return side; }
    public long getQuantity() { return quantity; }
    public BigDecimal getLimitPrice() { return limitPrice; }
    public OrderStatus getStatus() { return status; }
    public long getFilledQuantity() { return filledQuantity; }
    public Instant getPlacedAt() { return placedAt; }
    public Instant getCancelledAt() { return cancelledAt; }

    /** Cancels the unfilled remainder. Cancelling twice is a no-op, so retries are safe. */
    public void cancel(Instant at) {
        if (status == OrderStatus.CANCELLED || status == OrderStatus.FILLED) {
            return;
        }
        status = OrderStatus.CANCELLED;
        cancelledAt = at;
    }

    public void recordFill(long qty) {
        filledQuantity = Math.min(quantity, filledQuantity + qty);
        if (status != OrderStatus.CANCELLED) {
            status = filledQuantity >= quantity ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
        }
    }
}
