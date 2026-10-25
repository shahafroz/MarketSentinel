package com.marketsentinel.edgar;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsiderTransactionRepository extends JpaRepository<InsiderTransaction, Long> {

    boolean existsByTenantIdAndAccessionAndLineNo(UUID tenantId, String accession, int lineNo);

    List<InsiderTransaction> findByTenantIdAndTickerOrderByTransactionDateDesc(UUID tenantId, String ticker);

    List<InsiderTransaction> findByTenantIdOrderByTransactionDateDesc(UUID tenantId);
}
