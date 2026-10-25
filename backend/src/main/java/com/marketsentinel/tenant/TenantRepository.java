package com.marketsentinel.tenant;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByName(String name);

    Optional<Tenant> findByStripeCustomerId(String stripeCustomerId);
}
