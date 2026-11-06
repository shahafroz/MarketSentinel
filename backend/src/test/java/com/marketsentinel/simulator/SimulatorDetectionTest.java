package com.marketsentinel.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketsentinel.alert.Pattern;
import com.marketsentinel.detection.Detector;
import com.marketsentinel.detection.Finding;
import com.marketsentinel.detection.FrontRunningDetector;
import com.marketsentinel.detection.MarketDay;
import com.marketsentinel.detection.MarkingTheCloseDetector;
import com.marketsentinel.detection.PumpAndDumpDetector;
import com.marketsentinel.detection.SpoofingDetector;
import com.marketsentinel.detection.WashTradingDetector;
import com.marketsentinel.market.MarketOrder;
import com.marketsentinel.market.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** End to end without a database: simulate a day, run every detector, check each hidden scheme is caught. */
class SimulatorDetectionTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");
    private static final UUID TENANT = UUID.randomUUID();

    private final List<Detector> detectors = List.of(
            new WashTradingDetector(3),
            new SpoofingDetector(5, 10, 3, 60),
            new MarkingTheCloseDetector(10, new BigDecimal("0.6"), new BigDecimal("0.2"), new BigDecimal("0.01")),
            new PumpAndDumpDetector(new BigDecimal("0.15"), new BigDecimal("0.7")),
            new FrontRunningDetector(5, 10_000));

    @ParameterizedTest
    @ValueSource(longs = { 1, 42, 2026, 987654321 })
    void everyInjectedSchemeIsDetectedOnItsSymbol(long seed) {
        LocalDate date = LocalDate.of(2026, 10, 20);
        MarketSimulator.Result result = new MarketSimulator(NY).generate(date, seed);
        MarketDay day = toMarketDay(result.day());

        List<Finding> findings = detectors.stream().flatMap(d -> d.detect(day).stream()).toList();
        Map<Pattern, List<String>> symbolsByPattern = findings.stream()
                .collect(Collectors.groupingBy(Finding::pattern, Collectors.mapping(Finding::symbol, Collectors.toList())));

        assertThat(symbolsByPattern.get(Pattern.WASH_TRADING)).containsExactly("AURA");
        assertThat(symbolsByPattern.get(Pattern.SPOOFING)).containsExactly("BRISK");
        assertThat(symbolsByPattern.get(Pattern.MARKING_THE_CLOSE)).contains("CIRRO");
        assertThat(symbolsByPattern.get(Pattern.PUMP_AND_DUMP)).containsExactly("EMBER");
        assertThat(symbolsByPattern.get(Pattern.FRONT_RUNNING)).containsExactly("FALCO");
        assertThat(findings.stream().filter(f -> f.pattern() == Pattern.PUMP_AND_DUMP).findFirst().orElseThrow().accounts())
                .containsExactly("PD-1", "PD-2");
    }

    static MarketDay toMarketDay(SimulatedDay sim) {
        List<Trade> trades = sim.trades.stream()
                .map(t -> new Trade(TENANT, t.ref(), t.symbol(), t.buyAccount(), t.sellAccount(), t.buyOrder(),
                        t.sellOrder(), t.price(), t.qty(), t.executedAt(), t.ref()))
                .toList();
        List<MarketOrder> orders = sim.orders.stream().map(o -> {
            MarketOrder order = new MarketOrder(TENANT, o.ref(), o.account(), o.symbol(), o.side(), o.qty(), o.price(),
                    o.placedAt(), o.ref());
            if (o.cancelledAt() != null) {
                order.cancel(o.cancelledAt());
            } else {
                order.recordFill(o.filled());
            }
            return order;
        }).toList();
        Map<String, MarketDay.AccountInfo> accounts = sim.accounts.values().stream()
                .collect(Collectors.toMap(SimulatedDay.AccountRow::ref,
                        a -> new MarketDay.AccountInfo(a.owner(), a.type())));
        return new MarketDay(sim.date, NY, trades, orders, accounts);
    }
}
