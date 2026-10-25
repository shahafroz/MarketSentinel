package com.marketsentinel.detection;

import com.marketsentinel.alert.Pattern;
import com.marketsentinel.market.MarketOrder;
import com.marketsentinel.market.OrderStatus;
import com.marketsentinel.market.Side;
import com.marketsentinel.market.Trade;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spoofing / layering: an account places large orders it never intends to fill, to make the price
 * move, cancels them within seconds, and trades on the OTHER side at the better price it created.
 * Signal: several large orders cancelled quickly, plus executions on the opposite side nearby.
 */
public class SpoofingDetector implements Detector {

    private final int largeMultiple;
    private final Duration maxCancelTime;
    private final int minCancels;
    private final Duration oppositeWindow;

    public SpoofingDetector(int largeMultiple, int cancelSeconds, int minCancels, int oppositeWindowSeconds) {
        this.largeMultiple = largeMultiple;
        this.maxCancelTime = Duration.ofSeconds(cancelSeconds);
        this.minCancels = minCancels;
        this.oppositeWindow = Duration.ofSeconds(oppositeWindowSeconds);
    }

    @Override
    public Pattern pattern() {
        return Pattern.SPOOFING;
    }

    @Override
    public List<Finding> detect(MarketDay day) {
        List<Finding> findings = new ArrayList<>();
        Map<String, List<Trade>> tradesBySymbol = day.tradesBySymbol();

        for (var entry : day.ordersBySymbol().entrySet()) {
            String symbol = entry.getKey();
            List<MarketOrder> orders = entry.getValue();
            long median = medianQuantity(orders);
            long largeThreshold = Math.max(1, median * largeMultiple);

            Map<String, List<MarketOrder>> quickCancelsByAccount = orders.stream()
                    .filter(o -> o.getQuantity() >= largeThreshold)
                    .filter(o -> o.getStatus() == OrderStatus.CANCELLED && o.getCancelledAt() != null)
                    .filter(o -> o.getFilledQuantity() == 0)
                    .filter(o -> !Duration.between(o.getPlacedAt(), o.getCancelledAt()).minus(maxCancelTime).isPositive())
                    .collect(Collectors.groupingBy(MarketOrder::getAccountRef));

            for (var acc : quickCancelsByAccount.entrySet()) {
                List<MarketOrder> spoofs = acc.getValue();
                if (spoofs.size() < minCancels) {
                    continue;
                }
                spoofs.sort(Comparator.comparing(MarketOrder::getPlacedAt));
                long buySpoofs = spoofs.stream().filter(o -> o.getSide() == Side.BUY).count();
                Side spoofSide = buySpoofs * 2 >= spoofs.size() ? Side.BUY : Side.SELL;
                Side realSide = spoofSide.opposite();

                Instant from = spoofs.getFirst().getPlacedAt().minus(oppositeWindow);
                Instant to = spoofs.stream().map(MarketOrder::getCancelledAt).max(Instant::compareTo).orElseThrow()
                        .plus(oppositeWindow);
                String account = acc.getKey();
                List<Trade> realTrades = tradesBySymbol.getOrDefault(symbol, List.of()).stream()
                        .filter(t -> !t.getExecutedAt().isBefore(from) && !t.getExecutedAt().isAfter(to))
                        .filter(t -> account.equals(realSide == Side.BUY ? t.getBuyAccountRef() : t.getSellAccountRef()))
                        .toList();
                if (realTrades.isEmpty()) {
                    continue;
                }

                long cancelledQty = spoofs.stream().mapToLong(MarketOrder::getQuantity).sum();
                long realQty = realTrades.stream().mapToLong(Trade::getQuantity).sum();
                double avgSeconds = spoofs.stream()
                        .mapToLong(o -> Duration.between(o.getPlacedAt(), o.getCancelledAt()).toMillis())
                        .average().orElse(0) / 1000.0;

                int score = 55 + Math.min(30, spoofs.size() * 3) + (cancelledQty >= realQty * 5 ? 15 : 5);
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("medianOrderSize", median);
                evidence.put("largeOrderThreshold", largeThreshold);
                evidence.put("spoofSide", spoofSide);
                evidence.put("quickCancels", spoofs.size());
                evidence.put("cancelledShares", cancelledQty);
                evidence.put("avgSecondsToCancel", Math.round(avgSeconds * 10) / 10.0);
                evidence.put("oppositeSide", realSide);
                evidence.put("oppositeTrades", realTrades.size());
                evidence.put("oppositeShares", realQty);
                evidence.put("orderRefs", spoofs.stream().map(MarketOrder::getOrderRef).toList());
                evidence.put("tradeRefs", realTrades.stream().map(Trade::getTradeRef).toList());

                findings.add(new Finding(Pattern.SPOOFING, symbol, List.of(account), score,
                        "Spoofing in " + symbol + ": " + spoofs.size() + " large " + spoofSide
                                + " orders cancelled within seconds",
                        account + " placed " + spoofs.size() + " " + spoofSide + " orders of at least "
                                + largeThreshold + " shares (" + largeMultiple + "x the median), cancelled each one in "
                                + evidence.get("avgSecondsToCancel") + " s on average, and "
                                + (realSide == Side.SELL ? "sold " : "bought ") + realQty + " shares in the same minutes.",
                        evidence, day.date(), from.plus(oppositeWindow), to.minus(oppositeWindow)));
            }
        }
        return findings;
    }

    static long medianQuantity(List<MarketOrder> orders) {
        long[] sizes = orders.stream().mapToLong(MarketOrder::getQuantity).sorted().toArray();
        if (sizes.length == 0) {
            return 0;
        }
        int mid = sizes.length / 2;
        return sizes.length % 2 == 1 ? sizes[mid] : (sizes[mid - 1] + sizes[mid]) / 2;
    }
}
