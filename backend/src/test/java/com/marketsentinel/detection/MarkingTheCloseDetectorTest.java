package com.marketsentinel.detection;

import static com.marketsentinel.detection.TestMarket.day;
import static com.marketsentinel.detection.TestMarket.trade;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarkingTheCloseDetectorTest {

    private final MarkingTheCloseDetector detector = new MarkingTheCloseDetector(10, new BigDecimal("0.6"),
            new BigDecimal("0.2"), new BigDecimal("0.01"));

    @Test
    void heavyBuyingInTheLastMinutesThatLiftsTheCloseIsFlagged() {
        var day = day(List.of(
                trade("CCC", "A", "B", "50.00", 500, "11:00:00"),
                trade("CCC", "MK", "B", "50.00", 100, "12:00:00"),
                trade("CCC", "A", "B", "50.00", 500, "15:45:00"),
                trade("CCC", "MK", "C", "50.40", 3000, "15:52:00"),
                trade("CCC", "MK", "D", "50.80", 3000, "15:55:00"),
                trade("CCC", "MK", "E", "51.20", 3000, "15:59:00")));

        assertThat(detector.detect(day)).singleElement().satisfies(f -> {
            assertThat(f.accounts()).containsExactly("MK");
            assertThat(f.title()).contains("pushed up 2%");
            assertThat(f.evidence()).containsEntry("netSharesInWindow", 9000L);
        });
    }

    @Test
    void closingTradesThatDoNotMoveThePriceAreClean() {
        var day = day(List.of(
                trade("CCC", "A", "B", "50.00", 500, "11:00:00"),
                trade("CCC", "MK", "C", "50.00", 3000, "15:52:00"),
                trade("CCC", "MK", "D", "50.10", 3000, "15:59:00")));
        assertThat(detector.detect(day)).isEmpty();
    }

    @Test
    void accountThatTradesAllDayIsNotConcentratedAtTheClose() {
        var day = day(List.of(
                trade("CCC", "MK", "B", "50.00", 9000, "10:00:00"),
                trade("CCC", "A", "B", "50.00", 500, "15:45:00"),
                trade("CCC", "MK", "C", "51.00", 1000, "15:59:00")));
        assertThat(detector.detect(day)).isEmpty();
    }

    @Test
    void selfTradeAtTheCloseDoesNotCrash() {
        var day = day(List.of(
                trade("CCC", "A", "B", "50.00", 500, "11:00:00"),
                trade("CCC", "S", "S", "52.00", 500, "15:59:00")));
        assertThat(detector.detect(day)).isEmpty();
    }
}
