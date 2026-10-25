package com.marketsentinel.pipeline;

import com.marketsentinel.config.SentinelProperties;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Wiring for the two pipeline modes, chosen by `sentinel.ingest-mode`.
 *
 * kafka: the ingest API publishes "tenantId|date" to the `trades` topic (keyed by tenant, so one
 * firm's events stay in order) and returns at once; the detection engine consumes the topic.
 * Kafka delivers at least once, which is fine: marking a day twice is harmless.
 */
@Configuration
public class PipelineConfig {

    private static final Logger log = LoggerFactory.getLogger(PipelineConfig.class);

    @Bean
    @ConditionalOnProperty(name = "sentinel.ingest-mode", havingValue = "inline", matchIfMissing = true)
    TradeEventPublisher inlinePublisher(PendingDetections pending) {
        return pending::mark;
    }

    @Bean
    @ConditionalOnProperty(name = "sentinel.ingest-mode", havingValue = "kafka")
    TradeEventPublisher kafkaPublisher(KafkaTemplate<String, String> kafka, SentinelProperties props) {
        return (tenantId, date) -> kafka.send(props.kafkaTopic(), tenantId.toString(), tenantId + "|" + date);
    }

    @Bean
    @ConditionalOnProperty(name = "sentinel.ingest-mode", havingValue = "kafka")
    NewTopic tradesTopic(SentinelProperties props) {
        return TopicBuilder.name(props.kafkaTopic()).partitions(3).replicas(1).build();
    }

    /** The detection engine's side of the Kafka topic. */
    @Component
    @ConditionalOnProperty(name = "sentinel.ingest-mode", havingValue = "kafka")
    static class TradeEventConsumer {

        private final PendingDetections pending;

        TradeEventConsumer(PendingDetections pending) {
            this.pending = pending;
        }

        @KafkaListener(topics = "${sentinel.kafka-topic}")
        void onEvent(String message) {
            String[] parts = message.split("\\|");
            if (parts.length != 2) {
                log.warn("Skipping malformed event: {}", message);
                return;
            }
            pending.mark(UUID.fromString(parts[0]), LocalDate.parse(parts[1]));
        }
    }
}
