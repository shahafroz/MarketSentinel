package com.marketsentinel.pipeline;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Tells the detection engine that a firm's trading day has new data. Two implementations:
 * in-process (default, simple to run) or Kafka (durable, the way trading systems are built).
 */
public interface TradeEventPublisher {

    void dayChanged(UUID tenantId, LocalDate date);
}
