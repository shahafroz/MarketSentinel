package com.marketsentinel.detection;

import com.marketsentinel.market.AccountType;
import com.marketsentinel.market.MarketOrder;
import com.marketsentinel.market.Trade;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Everything a detector needs about one trading day of one firm, already loaded from the database.
 * Detectors never touch the database: they get a MarketDay and return findings.
 */
public record MarketDay(
        LocalDate date,
        ZoneId zone,
        List<Trade> trades,
        List<MarketOrder> orders,
        Map<String, AccountInfo> accounts) {

    /** US equities regular session: 9:30 am to 4:00 pm New York time. */
    public static final LocalTime OPEN = LocalTime.of(9, 30);
    public static final LocalTime CLOSE = LocalTime.of(16, 0);

    public record AccountInfo(String ownerRef, AccountType type) {
    }

    public Instant at(LocalTime time) {
        return date.atTime(time).atZone(zone).toInstant();
    }

    public Instant closeTime() {
        return at(CLOSE);
    }

    /** The beneficial owner of an account; an unknown account is treated as its own owner. */
    public String ownerOf(String accountRef) {
        AccountInfo info = accounts.get(accountRef);
        return info == null ? accountRef : info.ownerRef();
    }

    public AccountType typeOf(String accountRef) {
        AccountInfo info = accounts.get(accountRef);
        return info == null ? AccountType.CLIENT : info.type();
    }

    public Map<String, List<Trade>> tradesBySymbol() {
        return trades.stream().collect(Collectors.groupingBy(Trade::getSymbol));
    }

    public Map<String, List<MarketOrder>> ordersBySymbol() {
        return orders.stream().collect(Collectors.groupingBy(MarketOrder::getSymbol));
    }
}
