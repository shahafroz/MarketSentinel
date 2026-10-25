package com.marketsentinel.detection;

import static com.marketsentinel.detection.TestMarket.day;
import static com.marketsentinel.detection.TestMarket.trade;
import static org.assertj.core.api.Assertions.assertThat;

import com.marketsentinel.market.Trade;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PumpAndDumpDetectorTest {

    private final PumpAndDumpDetector detector = new PumpAndDumpDetector(new BigDecimal("0.15"), new BigDecimal("0.7"));

    @Test
    void accumulateHypeAndSellNearTheTopIsFlagged() {
        List<Trade> trades = new ArrayList<>();
        trades.add(trade("EEE", "R1", "R2", "4.00", 500, "09:31:00"));
        trades.add(trade("EEE", "PD", "R3", "4.00", 10_000, "09:40:00"));
        trades.add(trade("EEE", "R4", "R5", "4.50", 500, "11:00:00"));
        trades.add(trade("EEE", "R6", "R7", "5.20", 500, "12:00:00"));
        trades.add(trade("EEE", "R8", "PD", "5.70", 9_000, "12:50:00"));
        trades.add(trade("EEE", "R9", "R1", "5.80", 500, "13:00:00"));
        trades.add(trade("EEE", "R2", "R3", "4.60", 500, "15:00:00"));

        assertThat(detector.detect(day(trades))).singleElement().satisfies(f -> {
            assertThat(f.accounts()).containsExactly("PD");
            assertThat(f.evidence()).containsEntry("rise", new BigDecimal("0.4500"));
            assertThat(f.evidence().get("estimatedProfit")).isEqualTo(new BigDecimal("15300.00"));
        });
    }

    @Test
    void steadyPriceIsClean() {
        List<Trade> trades = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            trades.add(trade("EEE", "A" + i, "B" + i, "10.0" + i, 500, "1%d:00:00".formatted(i % 6)));
        }
        assertThat(detector.detect(day(trades))).isEmpty();
    }

    @Test
    void holderWhoKeepsTheSharesIsNotADumper() {
        List<Trade> trades = new ArrayList<>();
        trades.add(trade("EEE", "R1", "R2", "4.00", 500, "09:31:00"));
        trades.add(trade("EEE", "LONG", "R3", "4.00", 10_000, "09:40:00"));
        trades.add(trade("EEE", "R4", "R5", "4.50", 500, "11:00:00"));
        trades.add(trade("EEE", "R8", "LONG", "5.70", 1_000, "12:50:00"));
        trades.add(trade("EEE", "R9", "R1", "5.80", 500, "13:00:00"));
        assertThat(detector.detect(day(trades))).isEmpty();
    }
}
