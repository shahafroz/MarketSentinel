package com.marketsentinel.detection;

import com.marketsentinel.alert.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * What a detector found. It becomes an Alert when saved. `evidence` holds the numbers an analyst
 * needs to see why it was flagged; `fingerprint` identifies the finding so re-running detection
 * never creates a duplicate alert.
 */
public record Finding(
        Pattern pattern,
        String symbol,
        List<String> accounts,
        int score,
        String title,
        String reason,
        Map<String, Object> evidence,
        LocalDate tradeDate,
        Instant windowStart,
        Instant windowEnd) {

    public Finding {
        accounts = accounts.stream().distinct().sorted().toList();
        score = Math.max(0, Math.min(100, score));
    }

    public String fingerprint() {
        return pattern + "|" + symbol + "|" + tradeDate + "|" + String.join(",", accounts) + "|"
                + windowStart.getEpochSecond();
    }
}
