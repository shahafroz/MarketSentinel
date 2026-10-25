package com.marketsentinel.caseflow;

/** OPEN → (ESCALATED) → CLOSED_FALSE_POSITIVE or CLOSED_REPORTED. Closed cases cannot change. */
public enum CaseStatus {
    OPEN,
    ESCALATED,
    CLOSED_FALSE_POSITIVE,
    CLOSED_REPORTED;

    public boolean isClosed() {
        return this == CLOSED_FALSE_POSITIVE || this == CLOSED_REPORTED;
    }
}
