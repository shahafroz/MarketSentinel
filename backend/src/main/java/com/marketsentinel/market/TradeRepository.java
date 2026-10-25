package com.marketsentinel.market;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    Optional<Trade> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    boolean existsByTenantIdAndTradeRef(UUID tenantId, String tradeRef);

    List<Trade> findByTenantIdAndExecutedAtBetweenOrderByExecutedAt(UUID tenantId, Instant from, Instant to);

    List<Trade> findByTenantIdAndSymbolAndExecutedAtBetweenOrderByExecutedAt(UUID tenantId, String symbol,
            Instant from, Instant to);

    List<Trade> findByTenantIdOrderByExecutedAtDesc(UUID tenantId, Pageable page);

    long countByTenantId(UUID tenantId);

    @Query("select max(t.executedAt) from Trade t where t.tenantId = :tenantId")
    Optional<Instant> latestExecution(@Param("tenantId") UUID tenantId);
}
