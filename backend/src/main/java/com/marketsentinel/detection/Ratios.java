package com.marketsentinel.detection;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Exact ratio helpers. All prices are BigDecimal; doubles appear only in final display values. */
final class Ratios {

    static final MathContext MC = new MathContext(12, RoundingMode.HALF_EVEN);

    private Ratios() {
    }

    /** part / whole, or zero when whole is zero. */
    static BigDecimal share(long part, long whole) {
        return whole == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(part).divide(BigDecimal.valueOf(whole), MC);
    }

    /** (to - from) / from: +0.15 means a 15 % rise. */
    static BigDecimal change(BigDecimal from, BigDecimal to) {
        return from.signum() == 0 ? BigDecimal.ZERO : to.subtract(from).divide(from, MC);
    }

    /** Rounds a ratio to 4 decimals for evidence JSON (0.1234 = 12.34 %). */
    static BigDecimal display(BigDecimal ratio) {
        return ratio.setScale(4, RoundingMode.HALF_EVEN);
    }

    static int pct(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_EVEN).intValue();
    }
}
