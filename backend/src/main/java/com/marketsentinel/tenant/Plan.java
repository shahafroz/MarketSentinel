package com.marketsentinel.tenant;

/** Subscription plans. The limit is the number of trades a firm may send per calendar month. */
public enum Plan {
    FREE(10_000),
    PRO(1_000_000),
    ENTERPRISE(Long.MAX_VALUE);

    private final long monthlyTradeLimit;

    Plan(long monthlyTradeLimit) {
        this.monthlyTradeLimit = monthlyTradeLimit;
    }

    public long monthlyTradeLimit() {
        return monthlyTradeLimit;
    }
}
