package com.marketsentinel.audit;

import com.marketsentinel.security.CurrentUser;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The audit trail is readable by auditors, supervisors and admins. There is no endpoint to change it. */
@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAnyRole('AUDITOR', 'SUPERVISOR', 'ADMIN')")
public class AuditController {

    private final AuditRepository audit;

    public AuditController(AuditRepository audit) {
        this.audit = audit;
    }

    public record AuditRow(Long id, Instant at, String actor, String action, String entityType, String entityId,
            String details) {

        static AuditRow of(AuditEvent e) {
            return new AuditRow(e.getId(), e.getCreatedAt(), e.getActor(), e.getAction(), e.getEntityType(), e.getEntityId(),
                    e.getDetails());
        }
    }

    public record AuditPage(List<AuditRow> items, int page, long total) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public AuditPage list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        var result = audit.findByTenantIdOrderByCreatedAtDesc(CurrentUser.get().tenantId(),
                PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size))));
        return new AuditPage(result.getContent().stream().map(AuditRow::of).toList(), result.getNumber(),
                result.getTotalElements());
    }
}
