package com.marketsentinel.caseflow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseRepository extends JpaRepository<InvestigationCase, Long> {

    Optional<InvestigationCase> findByIdAndTenantId(Long id, UUID tenantId);

    Optional<InvestigationCase> findByTenantIdAndAlertId(UUID tenantId, Long alertId);

    List<InvestigationCase> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);
}
