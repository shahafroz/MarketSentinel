package com.marketsentinel.edgar;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialEventRepository extends JpaRepository<MaterialEvent, Long> {

    boolean existsByTenantIdAndAccession(UUID tenantId, String accession);

    List<MaterialEvent> findByTenantIdAndTickerOrderByFiledOnDesc(UUID tenantId, String ticker);

    List<MaterialEvent> findByTenantIdOrderByFiledOnDesc(UUID tenantId);
}
