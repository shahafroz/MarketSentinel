package com.marketsentinel.caseflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An investigation opened from one alert. */
@Entity
@Table(name = "cases")
public class InvestigationCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, unique = true)
    private Long alertId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus status = CaseStatus.OPEN;

    private String assignee;
    private String aiDraft;
    private String aiModel;
    private String approvedSummary;
    private String approvedBy;
    private String closedReason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected InvestigationCase() {
    }

    public InvestigationCase(UUID tenantId, Long alertId, String assignee) {
        this.tenantId = tenantId;
        this.alertId = alertId;
        this.assignee = assignee;
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Long getAlertId() { return alertId; }
    public CaseStatus getStatus() { return status; }
    public String getAssignee() { return assignee; }
    public String getAiDraft() { return aiDraft; }
    public String getAiModel() { return aiModel; }
    public String getApprovedSummary() { return approvedSummary; }
    public String getApprovedBy() { return approvedBy; }
    public String getClosedReason() { return closedReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void assign(String assignee) {
        requireOpen();
        this.assignee = assignee;
        touch();
    }

    public void escalate() {
        requireOpen();
        this.status = CaseStatus.ESCALATED;
        touch();
    }

    public void close(CaseStatus outcome, String reason) {
        requireOpen();
        if (!outcome.isClosed()) {
            throw new IllegalArgumentException("Outcome must be a closed status");
        }
        this.status = outcome;
        this.closedReason = reason;
        touch();
    }

    public void saveAiDraft(String draft, String model) {
        requireOpen();
        this.aiDraft = draft;
        this.aiModel = model;
        touch();
    }

    /** A human signs off the summary. The approved text may differ from the AI draft. */
    public void approveSummary(String text, String approver) {
        requireOpen();
        this.approvedSummary = text;
        this.approvedBy = approver;
        touch();
    }

    private void requireOpen() {
        if (status.isClosed()) {
            throw new IllegalStateException("Case " + id + " is closed and cannot change");
        }
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
