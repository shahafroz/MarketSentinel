package com.marketsentinel.alert;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findByIdAndTenantId(Long id, UUID tenantId);

    boolean existsByTenantIdAndFingerprint(UUID tenantId, String fingerprint);

    @Query("""
            select a from Alert a
            where a.tenantId = :tenantId
              and (:status is null or a.status = :status)
              and (:pattern is null or a.pattern = :pattern)
              and (:symbol is null or a.symbol = :symbol)
            order by a.score desc, a.createdAt desc
            """)
    Page<Alert> search(@Param("tenantId") UUID tenantId, @Param("status") AlertStatus status,
            @Param("pattern") Pattern pattern, @Param("symbol") String symbol, Pageable page);

    List<Alert> findByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, AlertStatus status);
}
