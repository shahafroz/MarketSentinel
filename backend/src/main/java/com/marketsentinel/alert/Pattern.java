package com.marketsentinel.alert;

/** The market-abuse patterns MarketSentinel detects. */
public enum Pattern {
    WASH_TRADING("Wash trading"),
    SPOOFING("Spoofing / layering"),
    MARKING_THE_CLOSE("Marking the close"),
    PUMP_AND_DUMP("Pump and dump"),
    FRONT_RUNNING("Front-running"),
    INSIDER_BEFORE_NEWS("Insider trading before news");

    private final String label;

    Pattern(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
