package com.marketsentinel.detection;

import com.marketsentinel.alert.Pattern;
import com.marketsentinel.market.Trade;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pump and dump: promoters buy a stock cheaply, hype it (social media, chat groups) so the price
 * jumps, then sell to the new buyers near the top. Signal: an intraday rise of at least
 * `minRise`, with accounts that bought in the cheap half of the move and sold most of those shares
 * in the expensive half. Only positions of at least 10 % of the cheap-side buying count, so small
 * retail traders who happened to buy early and sell late are not flagged.
 */
public class PumpAndDumpDetector implements Detector {

    private static final Duration TOP_WINDOW = Duration.ofMinutes(30);
    private static final BigDecimal MIN_POSITION_SHARE = new BigDecimal("0.10");

    private final BigDecimal minRise;
    private final BigDecimal minSellShare;

    public PumpAndDumpDetector(BigDecimal minRise, BigDecimal minSellShare) {
        this.minRise = minRise;
        this.minSellShare = minSellShare;
    }

    @Override
    public Pattern pattern() {
        return Pattern.PUMP_AND_DUMP;
    }

    @Override
    public List<Finding> detect(MarketDay day) {
        List<Finding> findings = new ArrayList<>();
        for (var entry : day.tradesBySymbol().entrySet()) {
            String symbol = entry.getKey();
            List<Trade> trades = entry.getValue().stream().sorted(Comparator.comparing(Trade::getExecutedAt)).toList();
            if (trades.size() < 5) {
                continue;
            }
            BigDecimal open = trades.getFirst().getPrice();
            Trade peak = trades.stream().max(Comparator.comparing(Trade::getPrice)).orElseThrow();
            BigDecimal rise = Ratios.change(open, peak.getPrice());
            if (rise.compareTo(minRise) < 0) {
                continue;
            }
            // Midpoint of the move: below it is "cheap", above it is "expensive".
            BigDecimal mid = open.add(peak.getPrice()).divide(BigDecimal.TWO, 4, RoundingMode.HALF_EVEN);
            Instant peakTime = peak.getExecutedAt();
            Instant topFrom = peakTime.minus(TOP_WINDOW);

            Map<String, Long> boughtCheap = new HashMap<>();
            Map<String, BigDecimal> costCheap = new HashMap<>();
            Map<String, Long> soldHigh = new HashMap<>();
            Map<String, BigDecimal> proceedsHigh = new HashMap<>();
            for (Trade t : trades) {
                if (!t.getExecutedAt().isAfter(peakTime) && t.getPrice().compareTo(mid) < 0) {
                    boughtCheap.merge(t.getBuyAccountRef(), t.getQuantity(), Long::sum);
                    costCheap.merge(t.getBuyAccountRef(), t.notional(), BigDecimal::add);
                }
                if (!t.getExecutedAt().isBefore(topFrom) && t.getPrice().compareTo(mid) >= 0) {
                    soldHigh.merge(t.getSellAccountRef(), t.getQuantity(), Long::sum);
                    proceedsHigh.merge(t.getSellAccountRef(), t.notional(), BigDecimal::add);
                }
            }

            long cheapVolume = boughtCheap.values().stream().mapToLong(Long::longValue).sum();
            List<String> dumpers = new ArrayList<>();
            List<Map<String, Object>> perAccount = new ArrayList<>();
            BigDecimal totalProfit = BigDecimal.ZERO;
            for (var b : boughtCheap.entrySet()) {
                String acc = b.getKey();
                if (Ratios.share(b.getValue(), cheapVolume).compareTo(MIN_POSITION_SHARE) < 0) {
                    continue;
                }
                long sold = Math.min(soldHigh.getOrDefault(acc, 0L), b.getValue());
                BigDecimal soldShare = Ratios.share(sold, b.getValue());
                if (sold == 0 || soldShare.compareTo(minSellShare) < 0) {
                    continue;
                }
                BigDecimal avgCost = costCheap.get(acc).divide(BigDecimal.valueOf(b.getValue()), 4, RoundingMode.HALF_EVEN);
                BigDecimal avgSale = proceedsHigh.get(acc)
                        .divide(BigDecimal.valueOf(soldHigh.get(acc)), 4, RoundingMode.HALF_EVEN);
                BigDecimal profit = avgSale.subtract(avgCost).multiply(BigDecimal.valueOf(sold))
                        .setScale(2, RoundingMode.HALF_EVEN);
                totalProfit = totalProfit.add(profit);
                dumpers.add(acc);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("account", acc);
                row.put("boughtCheap", b.getValue());
                row.put("avgCost", avgCost);
                row.put("soldNearTop", sold);
                row.put("avgSale", avgSale);
                row.put("estimatedProfit", profit);
                perAccount.add(row);
            }
            if (dumpers.isEmpty()) {
                continue;
            }

            int score = 55 + Math.min(35, Ratios.pct(rise) / 2) + (dumpers.size() >= 2 ? 10 : 0);
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("openPrice", open);
            evidence.put("peakPrice", peak.getPrice());
            evidence.put("rise", Ratios.display(rise));
            evidence.put("peakTime", peakTime.toString());
            evidence.put("estimatedProfit", totalProfit);
            evidence.put("accounts", perAccount);

            findings.add(new Finding(Pattern.PUMP_AND_DUMP, symbol, dumpers, score,
                    "Pump and dump in " + symbol + ": +" + Ratios.pct(rise) + "% then " + dumpers.size()
                            + " account(s) sold into the rise",
                    symbol + " rose from " + open + " to " + peak.getPrice() + ". " + dumpers.size()
                            + " account(s) bought below " + mid + " and sold most of those shares near the top, for an "
                            + "estimated profit of $" + totalProfit + ".",
                    evidence, day.date(), trades.getFirst().getExecutedAt(), peakTime.plus(TOP_WINDOW)));
        }
        return findings;
    }
}
