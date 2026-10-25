package com.marketsentinel.detection;

import com.marketsentinel.alert.Pattern;
import com.marketsentinel.market.Trade;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Wash trading: the same beneficial owner on both sides of a trade (directly, or through linked
 * accounts). No real change of ownership happens; the volume is fake and misleads other investors.
 */
public class WashTradingDetector implements Detector {

    private final int minTrades;

    public WashTradingDetector(int minTrades) {
        this.minTrades = minTrades;
    }

    @Override
    public Pattern pattern() {
        return Pattern.WASH_TRADING;
    }

    @Override
    public List<Finding> detect(MarketDay day) {
        List<Finding> findings = new ArrayList<>();
        for (var entry : day.tradesBySymbol().entrySet()) {
            String symbol = entry.getKey();
            List<Trade> trades = entry.getValue();
            long symbolVolume = trades.stream().mapToLong(Trade::getQuantity).sum();

            Map<String, List<Trade>> selfTradesByOwner = trades.stream()
                    .filter(t -> day.ownerOf(t.getBuyAccountRef()).equals(day.ownerOf(t.getSellAccountRef())))
                    .collect(Collectors.groupingBy(t -> day.ownerOf(t.getBuyAccountRef())));

            for (var owned : selfTradesByOwner.entrySet()) {
                List<Trade> washes = owned.getValue();
                if (washes.size() < minTrades) {
                    continue;
                }
                washes.sort(Comparator.comparing(Trade::getExecutedAt));
                long washVolume = washes.stream().mapToLong(Trade::getQuantity).sum();
                var share = Ratios.share(washVolume, symbolVolume);
                TreeSet<String> accounts = new TreeSet<>();
                washes.forEach(t -> {
                    accounts.add(t.getBuyAccountRef());
                    accounts.add(t.getSellAccountRef());
                });

                int score = 50 + Math.min(40, Ratios.pct(share)) + (washes.size() >= 10 ? 10 : 0);
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("owner", owned.getKey());
                evidence.put("selfTrades", washes.size());
                evidence.put("sharesTraded", washVolume);
                evidence.put("symbolVolume", symbolVolume);
                evidence.put("shareOfSymbolVolume", Ratios.display(share));
                evidence.put("linkedAccounts", List.copyOf(accounts));
                evidence.put("tradeRefs", washes.stream().limit(50).map(Trade::getTradeRef).toList());

                findings.add(new Finding(Pattern.WASH_TRADING, symbol, List.copyOf(accounts), score,
                        "Wash trading in " + symbol + ": " + washes.size() + " trades with no change of owner",
                        accounts.size() + " accounts owned by " + owned.getKey() + " traded " + washVolume
                                + " shares of " + symbol + " with each other, " + Ratios.pct(share)
                                + "% of the day's volume.",
                        evidence, day.date(), washes.getFirst().getExecutedAt(), washes.getLast().getExecutedAt()));
            }
        }
        return findings;
    }
}
