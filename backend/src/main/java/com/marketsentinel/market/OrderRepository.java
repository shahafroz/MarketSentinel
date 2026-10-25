package com.marketsentinel.market;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<MarketOrder, Long> {

    Optional<MarketOrder> findByTenantIdAndOrderRef(UUID tenantId, String orderRef);

    Optional<MarketOrder> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    List<MarketOrder> findByTenantIdAndPlacedAtBetweenOrderByPlacedAt(UUID tenantId, Instant from, Instant to);

    List<MarketOrder> findByTenantIdAndSymbolAndPlacedAtBetweenOrderByPlacedAt(UUID tenantId, String symbol,
            Instant from, Instant to);
}
