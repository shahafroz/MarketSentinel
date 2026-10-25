package com.marketsentinel.detection;

import com.marketsentinel.alert.Pattern;
import com.marketsentinel.market.Trade;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Marking the close: an account concentrates its trading in the last minutes of the session to push
 * the closing price (used to value portfolios, options and fund NAVs) in its favour.
 * Signal: most of the account's daily volume is in the closing window, it is a big share of the
 * market's closing volume, and the price moved in the direction the account traded.
 */
public class MarkingTheCloseDetector implements Detector {

    private final int windowMinutes;
    private final BigDecimal minAccountShare;
    private final BigDecimal minMarketShare;
    private final BigDecimal minMove;

    public MarkingTheCloseDetector(int windowMinutes, BigDecimal minAccountShare, BigDecimal minMarketShare,
            BigDecimal minMove) {
        this.windowMinutes = windowMinutes;
        this.minAccountShare = minAccountShare;
        this.minMarketShare = minMarketShare;
        this.minMove = minMove;
    }

    @Override
    public Pattern pattern() {
        return Pattern.MARKING_THE_CLOSE;
    }

    @Override
    public List<Finding> detect(MarketDay day) {
        List<Finding> findings = new ArrayList<>();
        Instant close = day.closeTime();
        Instant windowStart = close.minusSeconds(windowMinutes * 60L);

        for (var entry : day.tradesBySymbol().entrySet()) {
            String symbol = entry.getKey();
            List<Trade> trades = entry.getValue().stream()
                    .filter(t -> t.getExecutedAt().isBefore(close.plusSeconds(1)))
                    .sorted(Comparator.comparing(Trade::getExecutedAt))
                    .toList();
            List<Trade> inWindow = trades.stream().filter(t -> !t.getExecutedAt().isBefore(windowStart)).toList();
            List<Trade> beforeWindow = trades.stream().filter(t -> t.getExecutedAt().isBefore(windowStart)).toList();
            if (inWindow.isEmpty() || beforeWindow.isEmpty()) {
                continue;
            }
            BigDecimal priceBefore = beforeWindow.getLast().getPrice();
            BigDecimal closePrice = inWindow.getLast().getPrice();
            BigDecimal move = Ratios.change(priceBefore, closePrice);
            long windowVolume = inWindow.stream().mapToLong(Trade::getQuantity).sum();

            Map<String, Long> dayVolume = new HashMap<>();
            Map<String, Long> closeVolume = new HashMap<>();
            Map<String, Long> closeNet = new HashMap<>();
            for (Trade t : trades) {
                boolean inClose = !t.getExecutedAt().isBefore(windowStart);
                // A self-trade (same account both sides) counts once, not twice.
                Set<String> parties = new HashSet<>(List.of(t.getBuyAccountRef(), t.getSellAccountRef()));
                for (String acc : parties) {
                    dayVolume.merge(acc, t.getQuantity(), Long::sum);
                    if (inClose) {
                        closeVolume.merge(acc, t.getQuantity(), Long::sum);
                    }
                }
                if (inClose) {
                    closeNet.merge(t.getBuyAccountRef(), t.getQuantity(), Long::sum);
                    closeNet.merge(t.getSellAccountRef(), -t.getQuantity(), Long::sum);
                }
            }

            for (var acc : closeVolume.entrySet()) {
                String account = acc.getKey();
                long accClose = acc.getValue();
                long net = closeNet.getOrDefault(account, 0L);
                BigDecimal accountShare = Ratios.share(accClose, dayVolume.get(account));
                BigDecimal marketShare = Ratios.share(accClose, windowVolume);
                boolean pushedUp = net > 0 && move.compareTo(minMove) >= 0;
                boolean pushedDown = net < 0 && move.negate().compareTo(minMove) >= 0;
                if (accountShare.compareTo(minAccountShare) < 0 || marketShare.compareTo(minMarketShare) < 0
                        || !(pushedUp || pushedDown)) {
                    continue;
                }

                int score = 45 + Math.min(25, Ratios.pct(marketShare) / 3) + Math.min(30, Ratios.pct(move.abs()) * 5);
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("windowMinutes", windowMinutes);
                evidence.put("accountCloseShares", accClose);
                evidence.put("accountDayShares", dayVolume.get(account));
                evidence.put("shareOfAccountVolume", Ratios.display(accountShare));
                evidence.put("shareOfClosingVolume", Ratios.display(marketShare));
                evidence.put("netSharesInWindow", net);
                evidence.put("priceBeforeWindow", priceBefore);
                evidence.put("closingPrice", closePrice);
                evidence.put("priceMove", Ratios.display(move));

                String direction = pushedUp ? "up" : "down";
                findings.add(new Finding(Pattern.MARKING_THE_CLOSE, symbol, List.of(account), score,
                        "Marking the close in " + symbol + ": price pushed " + direction + " "
                                + Ratios.pct(move.abs()) + "% in the last " + windowMinutes + " minutes",
                        account + " did " + Ratios.pct(accountShare) + "% of its daily " + symbol + " trading in the last "
                                + windowMinutes + " minutes, was " + Ratios.pct(marketShare)
                                + "% of the closing volume, and the price closed " + direction + " from " + priceBefore
                                + " to " + closePrice + ".",
                        evidence, day.date(), windowStart, close));
            }
        }
        return findings;
    }
}
