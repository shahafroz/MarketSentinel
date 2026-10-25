package com.marketsentinel.detection;

import static com.marketsentinel.detection.TestMarket.accounts;
import static com.marketsentinel.detection.TestMarket.cancelled;
import static com.marketsentinel.detection.TestMarket.day;
import static com.marketsentinel.detection.TestMarket.order;
import static com.marketsentinel.detection.TestMarket.trade;
import static org.assertj.core.api.Assertions.assertThat;

import com.marketsentinel.market.MarketOrder;
import com.marketsentinel.market.Side;
import com.marketsentinel.market.Trade;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpoofingDetectorTest {

    private final SpoofingDetector detector = new SpoofingDetector(5, 10, 3, 60);

    /** 20 ordinary 500-share orders, so the median is 500 and "large" means 2,500+. */
    private static List<MarketOrder> background() {
        List<MarketOrder> orders = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            orders.add(order("C" + i, "BBB", Side.BUY, 500, "20.00", "10:%02d:00".formatted(i)));
        }
        return orders;
    }

    @Test
    void largeOrdersCancelledInSecondsWithOppositeTradesAreFlagged() {
        List<MarketOrder> orders = background();
        orders.add(cancelled("SPF", "BBB", Side.BUY, 40_000, "19.98", "13:00:00", "13:00:03"));
        orders.add(cancelled("SPF", "BBB", Side.BUY, 40_000, "19.98", "13:00:20", "13:00:24"));
        orders.add(cancelled("SPF", "BBB", Side.BUY, 40_000, "19.98", "13:00:40", "13:00:42"));
        List<Trade> trades = List.of(trade("BBB", "C1", "SPF", "20.10", 1500, "13:00:30"));

        List<Finding> findings = detector.detect(day(trades, orders, accounts()));

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.accounts()).containsExactly("SPF");
            assertThat(f.evidence()).containsEntry("quickCancels", 3).containsEntry("spoofSide", Side.BUY)
                    .containsEntry("oppositeSide", Side.SELL).containsEntry("largeOrderThreshold", 2500L);
        });
    }

    @Test
    void quickCancelsWithoutAnOppositeTradeAreNotFlagged() {
        List<MarketOrder> orders = background();
        orders.add(cancelled("SPF", "BBB", Side.BUY, 40_000, "19.98", "13:00:00", "13:00:03"));
        orders.add(cancelled("SPF", "BBB", Side.BUY, 40_000, "19.98", "13:00:20", "13:00:24"));
        orders.add(cancelled("SPF", "BBB", Side.BUY, 40_000, "19.98", "13:00:40", "13:00:42"));
        assertThat(detector.detect(day(List.of(), orders, accounts()))).isEmpty();
    }

    @Test
    void slowCancelsAreNormalBehaviour() {
        List<MarketOrder> orders = background();
        orders.add(cancelled("SPF", "BBB", Side.BUY, 40_000, "19.98", "13:00:00", "13:05:00"));
        orders.add(cancelled("SPF", "BBB", Side.BUY, 40_000, "19.98", "13:10:00", "13:15:00"));
        orders.add(cancelled("SPF", "BBB", Side.BUY, 40_000, "19.98", "13:20:00", "13:25:00"));
        List<Trade> trades = List.of(trade("BBB", "C1", "SPF", "20.10", 1500, "13:00:30"));
        assertThat(detector.detect(day(trades, orders, accounts()))).isEmpty();
    }

    @Test
    void medianHandlesEvenAndOddCounts() {
        assertThat(SpoofingDetector.medianQuantity(List.of(
                order("A", "X", Side.BUY, 100, "1", "10:00:00"),
                order("A", "X", Side.BUY, 300, "1", "10:00:00")))).isEqualTo(200);
        assertThat(SpoofingDetector.medianQuantity(List.of())).isZero();
    }
}
