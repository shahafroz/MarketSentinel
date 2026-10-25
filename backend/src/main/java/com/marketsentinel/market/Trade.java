package com.marketsentinel.market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An execution: a buyer and a seller exchanged `quantity` shares of `symbol` at `price`. */
@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String tradeRef;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String buyAccountRef;

    @Column(nullable = false)
    private String sellAccountRef;

    private String buyOrderRef;
    private String sellOrderRef;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false)
    private Instant executedAt;

    @Column(nullable = false)
    private String idempotencyKey;

    protected Trade() {
    }

    public Trade(UUID tenantId, String tradeRef, String symbol, String buyAccountRef, String sellAccountRef,
            String buyOrderRef, String sellOrderRef, BigDecimal price, long quantity, Instant executedAt,
            String idempotencyKey) {
        this.tenantId = tenantId;
        this.tradeRef = tradeRef;
        this.symbol = symbol;
        this.buyAccountRef = buyAccountRef;
        this.sellAccountRef = sellAccountRef;
        this.buyOrderRef = buyOrderRef;
        this.sellOrderRef = sellOrderRef;
        this.price = price;
        this.quantity = quantity;
        this.executedAt = executedAt;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getTradeRef() { return tradeRef; }
    public String getSymbol() { return symbol; }
    public String getBuyAccountRef() { return buyAccountRef; }
    public String getSellAccountRef() { return sellAccountRef; }
    public String getBuyOrderRef() { return buyOrderRef; }
    public String getSellOrderRef() { return sellOrderRef; }
    public BigDecimal getPrice() { return price; }
    public long getQuantity() { return quantity; }
    public Instant getExecutedAt() { return executedAt; }

    /** Notional value = price × quantity, exact. */
    public BigDecimal notional() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
