package com.marketsentinel.caseflow;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseNoteRepository extends JpaRepository<CaseNote, Long> {

    List<CaseNote> findByTenantIdAndCaseIdOrderByCreatedAt(UUID tenantId, Long caseId);
}
