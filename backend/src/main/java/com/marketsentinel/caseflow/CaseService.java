package com.marketsentinel.caseflow;

import com.marketsentinel.ai.AiSummaryService;
import com.marketsentinel.alert.Alert;
import com.marketsentinel.alert.AlertRepository;
import com.marketsentinel.alert.AlertStatus;
import com.marketsentinel.alert.AlertViews.AlertSummary;
import com.marketsentinel.audit.AuditRepository;
import com.marketsentinel.audit.AuditService;
import com.marketsentinel.security.CurrentUser;
import com.marketsentinel.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** The investigation workflow. Every change is written to the audit trail in the same transaction. */
@Service
public class CaseService {

    private final CaseRepository cases;
    private final CaseNoteRepository notes;
    private final AlertRepository alerts;
    private final AuditService audit;
    private final AuditRepository auditRead;
    private final AiSummaryService ai;
    private final TransactionTemplate tx;

    public CaseService(CaseRepository cases, CaseNoteRepository notes, AlertRepository alerts, AuditService audit,
            AuditRepository auditRead, AiSummaryService ai, TransactionTemplate tx) {
        this.cases = cases;
        this.notes = notes;
        this.alerts = alerts;
        this.audit = audit;
        this.auditRead = auditRead;
        this.ai = ai;
        this.tx = tx;
    }

    public record NoteView(Long id, String author, String body, Instant createdAt) {
    }

    public record TimelineEntry(Instant at, String actor, String action, String details) {
    }

    public record CaseView(Long id, CaseStatus status, String assignee, String aiDraft, String aiModel,
            String approvedSummary, String approvedBy, String closedReason, Instant createdAt, Instant updatedAt,
            AlertSummary alert, List<NoteView> notes, List<TimelineEntry> timeline) {
    }

    @Transactional
    public CaseView open(CurrentUser me, Long alertId) {
        Alert alert = alert(me.tenantId(), alertId);
        InvestigationCase existing = cases.findByTenantIdAndAlertId(me.tenantId(), alertId).orElse(null);
        if (existing != null) {
            return view(existing);
        }
        if (alert.getStatus() == AlertStatus.CLOSED) {
            throw new IllegalStateException("Alert " + alertId + " is already closed");
        }
        InvestigationCase c = cases.save(new InvestigationCase(me.tenantId(), alertId, me.actor()));
        alert.setStatus(AlertStatus.IN_REVIEW);
        audit.record(me.tenantId(), me.actor(), "CASE_OPENED", "case", c.getId(), "from alert " + alertId);
        return view(c);
    }

    @Transactional(readOnly = true)
    public List<CaseView> list(UUID tenantId) {
        return cases.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream().map(c -> summaryView(c)).toList();
    }

    @Transactional(readOnly = true)
    public CaseView get(UUID tenantId, Long id) {
        return view(load(tenantId, id));
    }

    @Transactional
    public CaseView addNote(CurrentUser me, Long id, String body) {
        InvestigationCase c = load(me.tenantId(), id);
        if (c.getStatus().isClosed()) {
            throw new IllegalStateException("Case " + id + " is closed");
        }
        notes.save(new CaseNote(me.tenantId(), id, me.actor(), body.trim()));
        audit.record(me.tenantId(), me.actor(), "NOTE_ADDED", "case", id, truncate(body, 120));
        return view(c);
    }

    @Transactional
    public CaseView assign(CurrentUser me, Long id, String assignee) {
        InvestigationCase c = load(me.tenantId(), id);
        c.assign(assignee);
        audit.record(me.tenantId(), me.actor(), "CASE_ASSIGNED", "case", id, "to " + assignee);
        return view(c);
    }

    @Transactional
    public CaseView escalate(CurrentUser me, Long id) {
        InvestigationCase c = load(me.tenantId(), id);
        c.escalate();
        audit.record(me.tenantId(), me.actor(), "CASE_ESCALATED", "case", id, "");
        return view(c);
    }

    @Transactional
    public CaseView close(CurrentUser me, Long id, CaseStatus outcome, String reason) {
        InvestigationCase c = load(me.tenantId(), id);
        c.close(outcome, reason.trim());
        alert(me.tenantId(), c.getAlertId()).setStatus(AlertStatus.CLOSED);
        audit.record(me.tenantId(), me.actor(), "CASE_CLOSED", "case", id, outcome + ": " + truncate(reason, 200));
        return view(c);
    }

    /** The AI call runs outside the database transaction: a slow model must not hold a connection. */
    public CaseView aiDraft(CurrentUser me, Long id) {
        InvestigationCase c = tx.execute(s -> load(me.tenantId(), id));
        Alert alert = tx.execute(s -> alert(me.tenantId(), c.getAlertId()));
        List<CaseNote> caseNotes = tx.execute(s -> notes.findByTenantIdAndCaseIdOrderByCreatedAt(me.tenantId(), id));
        AiSummaryService.Draft draft = ai.draft(alert, caseNotes);
        return tx.execute(s -> {
            InvestigationCase fresh = load(me.tenantId(), id);
            fresh.saveAiDraft(draft.text(), draft.model());
            audit.record(me.tenantId(), me.actor(), "AI_DRAFT_CREATED", "case", id, "model " + draft.model());
            return view(fresh);
        });
    }

    @Transactional
    public CaseView approveSummary(CurrentUser me, Long id, String text) {
        InvestigationCase c = load(me.tenantId(), id);
        c.approveSummary(text.trim(), me.actor());
        boolean edited = c.getAiDraft() == null || !c.getAiDraft().trim().equals(text.trim());
        audit.record(me.tenantId(), me.actor(), "SUMMARY_APPROVED", "case", id,
                edited ? "approved with human edits" : "approved as drafted");
        return view(c);
    }

    private InvestigationCase load(UUID tenantId, Long id) {
        return cases.findByIdAndTenantId(id, tenantId).orElseThrow(() -> ApiException.notFound("Case " + id));
    }

    private Alert alert(UUID tenantId, Long alertId) {
        return alerts.findByIdAndTenantId(alertId, tenantId).orElseThrow(() -> ApiException.notFound("Alert " + alertId));
    }

    private CaseView view(InvestigationCase c) {
        List<NoteView> noteViews = notes.findByTenantIdAndCaseIdOrderByCreatedAt(c.getTenantId(), c.getId()).stream()
                .map(n -> new NoteView(n.getId(), n.getAuthor(), n.getBody(), n.getCreatedAt())).toList();
        List<TimelineEntry> timeline = auditRead
                .findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAt(c.getTenantId(), "case", String.valueOf(c.getId()))
                .stream().map(e -> new TimelineEntry(e.getCreatedAt(), e.getActor(), e.getAction(), e.getDetails())).toList();
        return new CaseView(c.getId(), c.getStatus(), c.getAssignee(), c.getAiDraft(), c.getAiModel(),
                c.getApprovedSummary(), c.getApprovedBy(), c.getClosedReason(), c.getCreatedAt(), c.getUpdatedAt(),
                AlertSummary.of(alert(c.getTenantId(), c.getAlertId())), noteViews, timeline);
    }

    private CaseView summaryView(InvestigationCase c) {
        return new CaseView(c.getId(), c.getStatus(), c.getAssignee(), null, null, null, c.getApprovedBy(), c.getClosedReason(),
                c.getCreatedAt(), c.getUpdatedAt(), AlertSummary.of(alert(c.getTenantId(), c.getAlertId())), List.of(),
                List.of());
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
