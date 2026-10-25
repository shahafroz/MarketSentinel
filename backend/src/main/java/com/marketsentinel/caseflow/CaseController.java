package com.marketsentinel.caseflow;

import com.marketsentinel.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who may do what: everyone (auditors included) can read; analysts, supervisors and admins can
 * open cases, add notes, escalate and close false positives; only supervisors and admins can
 * reassign, approve AI summaries and close a case as reported to the regulator.
 */
@RestController
@RequestMapping("/api/v1")
public class CaseController {

    private static final String WORKERS = "hasAnyRole('ANALYST', 'SUPERVISOR', 'ADMIN')";
    private static final String LEADS = "hasAnyRole('SUPERVISOR', 'ADMIN')";

    private final CaseService cases;

    public CaseController(CaseService cases) {
        this.cases = cases;
    }

    public record NoteRequest(@NotBlank @Size(max = 4000) String body) {
    }

    public record AssignRequest(@NotBlank @Size(max = 200) String assignee) {
    }

    public record CloseRequest(@NotNull CaseStatus outcome, @NotBlank @Size(max = 2000) String reason) {
    }

    public record ApproveRequest(@NotBlank @Size(max = 8000) String text) {
    }

    @PostMapping("/alerts/{alertId}/case")
    @PreAuthorize(WORKERS)
    public CaseService.CaseView open(@PathVariable Long alertId) {
        return cases.open(CurrentUser.get(), alertId);
    }

    @GetMapping("/cases")
    public List<CaseService.CaseView> list() {
        return cases.list(CurrentUser.get().tenantId());
    }

    @GetMapping("/cases/{id}")
    public CaseService.CaseView get(@PathVariable Long id) {
        return cases.get(CurrentUser.get().tenantId(), id);
    }

    @PostMapping("/cases/{id}/notes")
    @PreAuthorize(WORKERS)
    public CaseService.CaseView note(@PathVariable Long id, @Valid @RequestBody NoteRequest request) {
        return cases.addNote(CurrentUser.get(), id, request.body());
    }

    @PostMapping("/cases/{id}/assign")
    @PreAuthorize(LEADS)
    public CaseService.CaseView assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request) {
        return cases.assign(CurrentUser.get(), id, request.assignee());
    }

    @PostMapping("/cases/{id}/escalate")
    @PreAuthorize(WORKERS)
    public CaseService.CaseView escalate(@PathVariable Long id) {
        return cases.escalate(CurrentUser.get(), id);
    }

    @PostMapping("/cases/{id}/close")
    @PreAuthorize(WORKERS)
    public CaseService.CaseView close(@PathVariable Long id, @Valid @RequestBody CloseRequest request) {
        if (request.outcome() == CaseStatus.CLOSED_REPORTED && !hasRole("SUPERVISOR") && !hasRole("ADMIN")) {
            throw new AccessDeniedException("Only a supervisor can close a case as reported");
        }
        return cases.close(CurrentUser.get(), id, request.outcome(), request.reason());
    }

    @PostMapping("/cases/{id}/ai-draft")
    @PreAuthorize(WORKERS)
    public CaseService.CaseView aiDraft(@PathVariable Long id) {
        return cases.aiDraft(CurrentUser.get(), id);
    }

    @PostMapping("/cases/{id}/approve-summary")
    @PreAuthorize(LEADS)
    public CaseService.CaseView approve(@PathVariable Long id, @Valid @RequestBody ApproveRequest request) {
        return cases.approveSummary(CurrentUser.get(), id, request.text());
    }

    private static boolean hasRole(String role) {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
