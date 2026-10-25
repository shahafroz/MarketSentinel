package com.marketsentinel.detection;

import com.marketsentinel.alert.Pattern;
import com.marketsentinel.market.AccountType;
import com.marketsentinel.market.MarketOrder;
import com.marketsentinel.market.Side;
import com.marketsentinel.market.Trade;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Front-running: an employee or the firm's own (proprietary) account trades just before a large
 * client order in the same direction, to profit from the price move the client order will cause.
 * Signal: an employee/proprietary trade on the same side within `window` before a large client
 * order, often followed by the opposite trade afterwards (taking the profit).
 */
public class FrontRunningDetector implements Detector {

    private static final Duration UNWIND_WINDOW = Duration.ofMinutes(30);

    private final Duration window;
    private final long largeQuantity;

    public FrontRunningDetector(int windowMinutes, long largeQuantity) {
        this.window = Duration.ofMinutes(windowMinutes);
        this.largeQuantity = largeQuantity;
    }

    @Override
    public Pattern pattern() {
        return Pattern.FRONT_RUNNING;
    }

    @Override
    public List<Finding> detect(MarketDay day) {
        List<Finding> findings = new ArrayList<>();
        Map<String, List<Trade>> tradesBySymbol = day.tradesBySymbol();

        List<MarketOrder> largeClientOrders = day.orders().stream()
                .filter(o -> o.getQuantity() >= largeQuantity)
                .filter(o -> day.typeOf(o.getAccountRef()) == AccountType.CLIENT)
                .toList();

        for (MarketOrder client : largeClientOrders) {
            Instant from = client.getPlacedAt().minus(window);
            Instant at = client.getPlacedAt();
            Side side = client.getSide();
            List<Trade> symbolTrades = tradesBySymbol.getOrDefault(client.getSymbol(), List.of());

            // Insider-side trades: employee/prop account on the SAME side as the client, just before it.
            Map<String, List<Trade>> aheadByAccount = symbolTrades.stream()
                    .filter(t -> !t.getExecutedAt().isBefore(from) && t.getExecutedAt().isBefore(at))
                    .filter(t -> isInsider(day, side == Side.BUY ? t.getBuyAccountRef() : t.getSellAccountRef()))
                    .collect(Collectors.groupingBy(t -> side == Side.BUY ? t.getBuyAccountRef() : t.getSellAccountRef()));

            for (var ahead : aheadByAccount.entrySet()) {
                String account = ahead.getKey();
                List<Trade> before = ahead.getValue();
                long aheadQty = before.stream().mapToLong(Trade::getQuantity).sum();
                List<Trade> unwind = symbolTrades.stream()
                        .filter(t -> t.getExecutedAt().isAfter(at) && t.getExecutedAt().isBefore(at.plus(UNWIND_WINDOW)))
                        .filter(t -> account.equals(side == Side.BUY ? t.getSellAccountRef() : t.getBuyAccountRef()))
                        .toList();
                long unwindQty = unwind.stream().mapToLong(Trade::getQuantity).sum();
                long secondsAhead = Duration.between(before.getLast().getExecutedAt(), at).toSeconds();

                int score = 60 + (unwindQty > 0 ? 25 : 0) + (secondsAhead <= 60 ? 10 : 0);
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("clientOrder", client.getOrderRef());
                evidence.put("clientAccount", client.getAccountRef());
                evidence.put("clientSide", side);
                evidence.put("clientQuantity", client.getQuantity());
                evidence.put("clientPlacedAt", at.toString());
                evidence.put("insiderAccountType", day.typeOf(account));
                evidence.put("sharesAhead", aheadQty);
                evidence.put("secondsAhead", secondsAhead);
                evidence.put("sharesUnwoundAfter", unwindQty);
                evidence.put("tradeRefs", before.stream().map(Trade::getTradeRef).toList());

                findings.add(new Finding(Pattern.FRONT_RUNNING, client.getSymbol(), List.of(account, client.getAccountRef()),
                        score,
                        "Front-running in " + client.getSymbol() + ": " + day.typeOf(account).name().toLowerCase()
                                + " account traded " + secondsAhead + " s before a large client order",
                        account + " (" + day.typeOf(account).name().toLowerCase() + ") " + (side == Side.BUY ? "bought " : "sold ")
                                + aheadQty + " shares " + secondsAhead + " s before client order " + client.getOrderRef()
                                + " for " + client.getQuantity() + " shares on the same side"
                                + (unwindQty > 0 ? ", then reversed " + unwindQty + " shares after it." : "."),
                        evidence, day.date(), before.getFirst().getExecutedAt(), at));
            }
        }
        return findings;
    }

    private static boolean isInsider(MarketDay day, String accountRef) {
        AccountType type = day.typeOf(accountRef);
        return type == AccountType.EMPLOYEE || type == AccountType.PROPRIETARY;
    }
}
