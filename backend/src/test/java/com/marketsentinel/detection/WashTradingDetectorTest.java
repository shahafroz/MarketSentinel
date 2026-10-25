package com.marketsentinel.detection;

import static com.marketsentinel.detection.TestMarket.accounts;
import static com.marketsentinel.detection.TestMarket.day;
import static com.marketsentinel.detection.TestMarket.trade;
import static org.assertj.core.api.Assertions.assertThat;

import com.marketsentinel.alert.Pattern;
import com.marketsentinel.market.AccountType;
import java.util.List;
import org.junit.jupiter.api.Test;

class WashTradingDetectorTest {

    private final WashTradingDetector detector = new WashTradingDetector(3);

    @Test
    void normalTradingBetweenDifferentOwnersIsClean() {
        var day = day(List.of(
                trade("AAA", "A1", "B1", "10.00", 100, "10:00:00"),
                trade("AAA", "B1", "C1", "10.01", 100, "10:05:00"),
                trade("AAA", "C1", "A1", "10.02", 100, "10:10:00")));
        assertThat(detector.detect(day)).isEmpty();
    }

    @Test
    void linkedAccountsTradingWithEachOtherAreFlagged() {
        var accounts = accounts().add("X1", "OWNER", AccountType.CLIENT).add("X2", "OWNER", AccountType.CLIENT);
        var day = day(List.of(
                trade("AAA", "X1", "X2", "10.00", 1000, "11:00:00"),
                trade("AAA", "X2", "X1", "10.00", 1000, "11:05:00"),
                trade("AAA", "X1", "X2", "10.00", 1000, "11:10:00"),
                trade("AAA", "A1", "B1", "10.00", 1000, "11:15:00")), List.of(), accounts);

        List<Finding> findings = detector.detect(day);

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.pattern()).isEqualTo(Pattern.WASH_TRADING);
            assertThat(f.accounts()).containsExactly("X1", "X2");
            assertThat(f.evidence()).containsEntry("selfTrades", 3).containsEntry("sharesTraded", 3000L);
            assertThat(f.reason()).contains("75%");
        });
    }

    @Test
    void sameAccountOnBothSidesIsFlagged() {
        var day = day(List.of(
                trade("AAA", "S1", "S1", "5.00", 500, "09:40:00"),
                trade("AAA", "S1", "S1", "5.00", 500, "09:41:00"),
                trade("AAA", "S1", "S1", "5.00", 500, "09:42:00")));
        assertThat(detector.detect(day)).hasSize(1);
    }

    @Test
    void justBelowTheMinimumIsNotFlagged() {
        var accounts = accounts().add("X1", "OWNER", AccountType.CLIENT).add("X2", "OWNER", AccountType.CLIENT);
        var day = day(List.of(
                trade("AAA", "X1", "X2", "10.00", 1000, "11:00:00"),
                trade("AAA", "X2", "X1", "10.00", 1000, "11:05:00")), List.of(), accounts);
        assertThat(detector.detect(day)).isEmpty();
    }
}
