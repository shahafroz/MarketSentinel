package com.marketsentinel.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEvent, Long> {

    Page<AuditEvent> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable page);

    List<AuditEvent> findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAt(UUID tenantId, String entityType,
            String entityId);
}
