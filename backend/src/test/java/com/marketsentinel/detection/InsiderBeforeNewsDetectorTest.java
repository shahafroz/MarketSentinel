package com.marketsentinel.detection;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketsentinel.edgar.InsiderTransaction;
import com.marketsentinel.edgar.MaterialEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class InsiderBeforeNewsDetectorTest {

    private final InsiderBeforeNewsDetector detector = new InsiderBeforeNewsDetector(10, TestMarket.NY);

    private static InsiderTransaction tx(String owner, String code, boolean acquired, String date, String shares,
            String price, int line) {
        return new InsiderTransaction(TestMarket.TENANT, "ACME", "0000000001", "0000000001-26-00000" + line, line, owner,
                "Chief Executive Officer", LocalDate.parse(date), code, new BigDecimal(shares), new BigDecimal(price),
                acquired, LocalDate.parse(date).plusDays(2));
    }

    private static MaterialEvent eightK(String items, String filed) {
        return new MaterialEvent(TestMarket.TENANT, "ACME", "0000000001", "0000000001-26-009999", items,
                LocalDate.parse(filed));
    }

    @Test
    void openMarketPurchaseDaysBeforeADealAnnouncementIsFlagged() {
        var findings = detector.detect(List.of(tx("Jane Doe", "P", true, "2026-03-02", "20000", "15.00", 1)),
                List.of(eightK("1.01,9.01", "2026-03-05")));

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.accounts()).containsExactly("Jane Doe");
            assertThat(f.evidence()).containsEntry("daysBeforeFiling", 3L)
                    .containsEntry("estimatedValue", new BigDecimal("300000.00"));
            assertThat(f.score()).isEqualTo(95);
        });
    }

    @Test
    void salesAndGrantsAreNotPurchases() {
        var findings = detector.detect(List.of(
                tx("Jane Doe", "S", false, "2026-03-02", "20000", "15.00", 1),
                tx("Jane Doe", "A", true, "2026-03-02", "20000", "0", 2)),
                List.of(eightK("1.01", "2026-03-05")));
        assertThat(findings).isEmpty();
    }

    @Test
    void routineFilingsAreNotPriceSensitive() {
        var findings = detector.detect(List.of(tx("Jane Doe", "P", true, "2026-03-02", "20000", "15.00", 1)),
                List.of(eightK("9.01", "2026-03-05")));
        assertThat(findings).isEmpty();
    }

    @Test
    void purchaseLongBeforeTheNewsIsOutsideTheWindow() {
        var findings = detector.detect(List.of(tx("Jane Doe", "P", true, "2026-01-02", "20000", "15.00", 1)),
                List.of(eightK("1.01", "2026-03-05")));
        assertThat(findings).isEmpty();
    }
}
