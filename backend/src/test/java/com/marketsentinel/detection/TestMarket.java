package com.marketsentinel.detection;

import com.marketsentinel.market.AccountType;
import com.marketsentinel.market.MarketOrder;
import com.marketsentinel.market.Side;
import com.marketsentinel.market.Trade;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Small builders for hand-made trading days with known answers. */
final class TestMarket {

    static final ZoneId NY = ZoneId.of("America/New_York");
    static final LocalDate DATE = LocalDate.of(2026, 10, 20);
    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final AtomicInteger SEQ = new AtomicInteger();

    private TestMarket() {
    }

    static Instant at(String hhmmss) {
        return DATE.atTime(LocalTime.parse(hhmmss)).atZone(NY).toInstant();
    }

    static Trade trade(String symbol, String buyer, String seller, String price, long qty, String time) {
        String ref = "T" + SEQ.incrementAndGet();
        return new Trade(TENANT, ref, symbol, buyer, seller, null, null, new BigDecimal(price), qty, at(time), ref);
    }

    static MarketOrder order(String account, String symbol, Side side, long qty, String price, String time) {
        String ref = "O" + SEQ.incrementAndGet();
        return new MarketOrder(TENANT, ref, account, symbol, side, qty, new BigDecimal(price), at(time), ref);
    }

    static MarketOrder cancelled(String account, String symbol, Side side, long qty, String price, String placed,
            String cancelled) {
        MarketOrder o = order(account, symbol, side, qty, price, placed);
        o.cancel(at(cancelled));
        return o;
    }

    static Accounts accounts() {
        return new Accounts();
    }

    static final class Accounts {
        final Map<String, MarketDay.AccountInfo> map = new HashMap<>();

        Accounts add(String ref, String owner, AccountType type) {
            map.put(ref, new MarketDay.AccountInfo(owner, type));
            return this;
        }
    }

    static MarketDay day(List<Trade> trades, List<MarketOrder> orders, Accounts accounts) {
        return new MarketDay(DATE, NY, trades, orders, accounts.map);
    }

    static MarketDay day(List<Trade> trades) {
        return day(trades, List.of(), accounts());
    }
}
