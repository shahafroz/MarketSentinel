package com.marketsentinel.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** A possible case of market abuse raised by a detector, waiting for an analyst. */
@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Pattern pattern;

    @Column(nullable = false)
    private String symbol;

    /** Comma-separated account references involved. */
    @Column(nullable = false)
    private String accounts;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String reason;

    /** JSON with the numbers behind the alert. */
    @Column(nullable = false)
    private String evidence;

    @Column(nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false)
    private Instant windowStart;

    @Column(nullable = false)
    private Instant windowEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status = AlertStatus.NEW;

    @Column(nullable = false)
    private String fingerprint;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Alert() {
    }

    public Alert(UUID tenantId, Pattern pattern, String symbol, List<String> accounts, int score, String title,
            String reason, String evidence, LocalDate tradeDate, Instant windowStart, Instant windowEnd,
            String fingerprint) {
        this.tenantId = tenantId;
        this.pattern = pattern;
        this.symbol = symbol;
        this.accounts = String.join(",", accounts);
        this.score = Math.max(0, Math.min(100, score));
        this.title = title;
        this.reason = reason;
        this.evidence = evidence;
        this.tradeDate = tradeDate;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.fingerprint = fingerprint;
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Pattern getPattern() { return pattern; }
    public String getSymbol() { return symbol; }
    public int getScore() { return score; }
    public String getTitle() { return title; }
    public String getReason() { return reason; }
    public String getEvidence() { return evidence; }
    public LocalDate getTradeDate() { return tradeDate; }
    public Instant getWindowStart() { return windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
    public AlertStatus getStatus() { return status; }
    public String getFingerprint() { return fingerprint; }
    public Instant getCreatedAt() { return createdAt; }

    public List<String> getAccountList() {
        return accounts.isBlank() ? List.of() : Arrays.asList(accounts.split(","));
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }
}
