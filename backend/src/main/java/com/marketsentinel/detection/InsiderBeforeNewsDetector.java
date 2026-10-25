package com.marketsentinel.detection;

import com.marketsentinel.alert.Pattern;
import com.marketsentinel.edgar.InsiderTransaction;
import com.marketsentinel.edgar.MaterialEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Insider trading before news, using real SEC filings: an officer, director or 10 % owner makes an
 * open-market purchase (Form 4, code P) in the days before the company files a price-sensitive 8-K
 * (results, a material agreement, an acquisition, a change in control...).
 *
 * This is a screening signal, not proof: insiders often buy legitimately. An analyst decides.
 */
public class InsiderBeforeNewsDetector {

    private static final Set<String> DEAL_ITEMS = Set.of("1.01", "2.01", "5.01");
    private static final BigDecimal LARGE_NOTIONAL = new BigDecimal("250000");

    private final int daysBefore;
    private final ZoneId zone;

    public InsiderBeforeNewsDetector(int daysBefore, ZoneId zone) {
        this.daysBefore = daysBefore;
        this.zone = zone;
    }

    public List<Finding> detect(List<InsiderTransaction> transactions, List<MaterialEvent> events) {
        List<Finding> findings = new ArrayList<>();
        Map<String, List<InsiderTransaction>> purchasesByTicker = transactions.stream()
                .filter(InsiderTransaction::isOpenMarketPurchase)
                .collect(Collectors.groupingBy(InsiderTransaction::getTicker));

        for (MaterialEvent event : events) {
            if (!event.isPriceSensitive()) {
                continue;
            }
            LocalDate windowStart = event.getFiledOn().minusDays(daysBefore);
            Map<String, List<InsiderTransaction>> byOwner = purchasesByTicker.getOrDefault(event.getTicker(), List.of())
                    .stream()
                    .filter(t -> !t.getTransactionDate().isBefore(windowStart)
                            && t.getTransactionDate().isBefore(event.getFiledOn()))
                    .collect(Collectors.groupingBy(InsiderTransaction::getOwnerName));

            for (var owner : byOwner.entrySet()) {
                List<InsiderTransaction> buys = owner.getValue();
                BigDecimal shares = buys.stream().map(InsiderTransaction::getShares).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal notional = buys.stream()
                        .filter(t -> t.getPrice() != null)
                        .map(t -> t.getPrice().multiply(t.getShares()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_EVEN);
                LocalDate firstBuy = buys.stream().map(InsiderTransaction::getTransactionDate).min(LocalDate::compareTo)
                        .orElseThrow();
                long daysAhead = ChronoUnit.DAYS.between(firstBuy, event.getFiledOn());
                boolean deal = event.itemList().stream().anyMatch(DEAL_ITEMS::contains);

                int score = 50 + (deal ? 20 : 5) + (notional.compareTo(LARGE_NOTIONAL) >= 0 ? 15 : 0)
                        + (daysAhead <= 3 ? 10 : 0);
                String title = buys.getFirst().getOwnerTitle();
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("insider", owner.getKey());
                evidence.put("insiderTitle", title);
                evidence.put("purchases", buys.size());
                evidence.put("shares", shares);
                evidence.put("estimatedValue", notional);
                evidence.put("firstPurchase", firstBuy.toString());
                evidence.put("eightKFiled", event.getFiledOn().toString());
                evidence.put("eightKItems", event.itemList());
                evidence.put("daysBeforeFiling", daysAhead);
                evidence.put("form4Accessions", buys.stream().map(InsiderTransaction::getAccession).distinct().toList());
                evidence.put("eightKAccession", event.getAccession());
                evidence.put("source", "SEC EDGAR");

                findings.add(new Finding(Pattern.INSIDER_BEFORE_NEWS, event.getTicker(), List.of(owner.getKey()), score,
                        "Insider bought " + event.getTicker() + " " + daysAhead + " day(s) before an 8-K (items "
                                + event.getItems() + ")",
                        owner.getKey() + (title.isBlank() ? "" : " (" + title + ")") + " bought " + shares.stripTrailingZeros()
                                .toPlainString() + " shares (about $" + notional + ") in the open market starting "
                                + firstBuy + ", " + daysAhead + " day(s) before " + event.getTicker() + " filed an 8-K with items "
                                + event.getItems() + ".",
                        evidence, event.getFiledOn(), firstBuy.atTime(LocalTime.NOON).atZone(zone).toInstant(),
                        event.getFiledOn().atTime(LocalTime.NOON).atZone(zone).toInstant()));
            }
        }
        return findings;
    }
}
