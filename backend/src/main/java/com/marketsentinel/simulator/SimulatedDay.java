package com.marketsentinel.simulator;

import com.marketsentinel.market.AccountType;
import com.marketsentinel.market.OrderStatus;
import com.marketsentinel.market.Side;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** In-memory builder for one simulated trading day: accounts, orders and trades, before saving. */
final class SimulatedDay {

    record AccountRow(String ref, String owner, AccountType type) {
    }

    record OrderRow(String ref, String account, String symbol, Side side, long qty, BigDecimal price,
            OrderStatus status, long filled, Instant placedAt, Instant cancelledAt) {
    }

    record TradeRow(String ref, String symbol, String buyAccount, String sellAccount, String buyOrder, String sellOrder,
            BigDecimal price, long qty, Instant executedAt) {
    }

    final LocalDate date;
    final ZoneId zone;
    final Random random;
    final String prefix;
    final Map<String, AccountRow> accounts = new LinkedHashMap<>();
    final List<OrderRow> orders = new ArrayList<>();
    final List<TradeRow> trades = new ArrayList<>();
    private int orderSeq;
    private int tradeSeq;

    SimulatedDay(LocalDate date, ZoneId zone, long seed) {
        this.date = date;
        this.zone = zone;
        this.random = new Random(seed);
        this.prefix = "SIM-" + date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    void account(String ref, String owner, AccountType type) {
        accounts.put(ref, new AccountRow(ref, owner, type));
    }

    Instant at(int hour, int minute, int second) {
        return date.atTime(LocalTime.of(hour, minute, second)).atZone(zone).toInstant();
    }

    /** Rounds a running price to the cent for a trade or order. */
    static BigDecimal price(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }

    /**
     * Moves a price by a ratio (e.g. 1.005 = up 0.5 %). Prices stay BigDecimal; only the ratio, often
     * random noise, is a double. Kept to 6 places so a random walk does not drift from rounding.
     */
    static BigDecimal times(BigDecimal price, double ratio) {
        return price.multiply(BigDecimal.valueOf(ratio)).setScale(6, RoundingMode.HALF_EVEN);
    }

    /** A filled trade, with the buy and sell orders that produced it (placed 1–5 s earlier). */
    void trade(String symbol, String buyer, String seller, BigDecimal price, long qty, Instant at) {
        String buyOrder = order(buyer, symbol, Side.BUY, qty, price, OrderStatus.FILLED, qty,
                at.minusSeconds(1 + random.nextInt(5)), null);
        String sellOrder = order(seller, symbol, Side.SELL, qty, price, OrderStatus.FILLED, qty,
                at.minusSeconds(1 + random.nextInt(5)), null);
        trades.add(new TradeRow(prefix + "-T" + (++tradeSeq), symbol, buyer, seller, buyOrder, sellOrder, price, qty, at));
    }

    /** A large order that is filled over several trades by different counterparties. */
    String bigOrder(String account, String symbol, Side side, long qty, BigDecimal limit, Instant placedAt) {
        return order(account, symbol, side, qty, limit, OrderStatus.FILLED, qty, placedAt, null);
    }

    void fillAgainst(String symbol, String bigOrderRef, String bigAccount, Side bigSide, String counterparty,
            BigDecimal price, long qty, Instant at) {
        String other = order(counterparty, symbol, bigSide.opposite(), qty, price, OrderStatus.FILLED, qty,
                at.minusSeconds(1 + random.nextInt(5)), null);
        boolean bigBuys = bigSide == Side.BUY;
        trades.add(new TradeRow(prefix + "-T" + (++tradeSeq), symbol, bigBuys ? bigAccount : counterparty,
                bigBuys ? counterparty : bigAccount, bigBuys ? bigOrderRef : other, bigBuys ? other : bigOrderRef, price,
                qty, at));
    }

    void cancelledOrder(String account, String symbol, Side side, long qty, BigDecimal price, Instant placedAt,
            Instant cancelledAt) {
        order(account, symbol, side, qty, price, OrderStatus.CANCELLED, 0, placedAt, cancelledAt);
    }

    private String order(String account, String symbol, Side side, long qty, BigDecimal price, OrderStatus status,
            long filled, Instant placedAt, Instant cancelledAt) {
        String ref = prefix + "-O" + (++orderSeq);
        orders.add(new OrderRow(ref, account, symbol, side, qty, price, status, filled, placedAt, cancelledAt));
        return ref;
    }
}
