package com.marketsentinel.config;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** All `sentinel.*` settings from application.yml, as one typed, validated-at-startup object. */
@ConfigurationProperties(prefix = "sentinel")
public record SentinelProperties(
        String ingestMode,
        String kafkaTopic,
        List<String> corsOrigins,
        int trustedProxyHops,
        Jwt jwt,
        Demo demo,
        Detection detection,
        Sec sec,
        Ai ai,
        Billing billing) {

    public boolean kafkaMode() {
        return "kafka".equalsIgnoreCase(ingestMode);
    }

    public record Jwt(String secret, long ttlMinutes) {
    }

    public record Demo(boolean seed, String password) {
    }

    public record Detection(
            ZoneId marketZone,
            int washMinTrades,
            int spoofLargeMultiple,
            int spoofCancelSeconds,
            int spoofMinCancels,
            int spoofOppositeWindowSeconds,
            int closeWindowMinutes,
            BigDecimal closeMinAccountShare,
            BigDecimal closeMinMarketShare,
            BigDecimal closeMinMove,
            BigDecimal pumpMinRise,
            BigDecimal pumpMinSellShare,
            int frontRunWindowMinutes,
            long frontRunLargeQty,
            int insiderDaysBefore) {
    }

    public record Sec(String userAgent, String baseUrl, String archivesUrl) {
    }

    public record Ai(String geminiApiKey, List<String> models, int timeoutSeconds) {

        public boolean enabled() {
            return geminiApiKey != null && !geminiApiKey.isBlank();
        }
    }

    public record Billing(String stripeSecretKey, String stripeWebhookSecret, String pricePro, String priceEnterprise,
            String appUrl) {

        public boolean enabled() {
            return stripeSecretKey != null && !stripeSecretKey.isBlank();
        }
    }
}
