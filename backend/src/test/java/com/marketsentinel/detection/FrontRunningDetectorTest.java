package com.marketsentinel.detection;

import static com.marketsentinel.detection.TestMarket.accounts;
import static com.marketsentinel.detection.TestMarket.day;
import static com.marketsentinel.detection.TestMarket.order;
import static com.marketsentinel.detection.TestMarket.trade;
import static org.assertj.core.api.Assertions.assertThat;

import com.marketsentinel.market.AccountType;
import com.marketsentinel.market.Side;
import java.util.List;
import org.junit.jupiter.api.Test;

class FrontRunningDetectorTest {

    private final FrontRunningDetector detector = new FrontRunningDetector(5, 10_000);

    private final TestMarket.Accounts accounts = accounts()
            .add("EMP", "EMP", AccountType.EMPLOYEE)
            .add("BIG", "BIG", AccountType.CLIENT)
            .add("C1", "C1", AccountType.CLIENT);

    @Test
    void employeeBuyingJustBeforeALargeClientBuyIsFlagged() {
        var trades = List.of(
                trade("FFF", "EMP", "C1", "30.00", 2000, "14:29:20"),
                trade("FFF", "C1", "EMP", "30.40", 2000, "14:50:00"));
        var orders = List.of(order("BIG", "FFF", Side.BUY, 25_000, "30.60", "14:30:00"));

        assertThat(detector.detect(day(trades, orders, accounts))).singleElement().satisfies(f -> {
            assertThat(f.accounts()).containsExactly("BIG", "EMP");
            assertThat(f.evidence()).containsEntry("secondsAhead", 40L).containsEntry("sharesUnwoundAfter", 2000L);
            assertThat(f.score()).isEqualTo(95);
        });
    }

    @Test
    void clientTradingBeforeAnotherClientIsNotFrontRunning() {
        var trades = List.of(trade("FFF", "C1", "EMP", "30.00", 2000, "14:29:20"));
        var orders = List.of(order("BIG", "FFF", Side.BUY, 25_000, "30.60", "14:30:00"));
        assertThat(detector.detect(day(trades, orders, accounts))).isEmpty();
    }

    @Test
    void employeeTradeOutsideTheWindowIsNotFlagged() {
        var trades = List.of(trade("FFF", "EMP", "C1", "30.00", 2000, "14:20:00"));
        var orders = List.of(order("BIG", "FFF", Side.BUY, 25_000, "30.60", "14:30:00"));
        assertThat(detector.detect(day(trades, orders, accounts))).isEmpty();
    }

    @Test
    void smallClientOrdersAreIgnored() {
        var trades = List.of(trade("FFF", "EMP", "C1", "30.00", 2000, "14:29:20"));
        var orders = List.of(order("BIG", "FFF", Side.BUY, 500, "30.60", "14:30:00"));
        assertThat(detector.detect(day(trades, orders, accounts))).isEmpty();
    }
}
