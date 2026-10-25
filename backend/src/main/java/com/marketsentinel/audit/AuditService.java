package com.marketsentinel.audit;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes audit entries. Joins the caller's transaction, so an action and its audit entry commit together. */
@Service
public class AuditService {

    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID tenantId, String actor, String action, String entityType, Object entityId,
            String details) {
        repository.save(new AuditEvent(tenantId, actor, action, entityType, String.valueOf(entityId), details));
    }
}
