package com.marketsentinel.market;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByTenantIdAndAccountRef(UUID tenantId, String accountRef);

    List<Account> findByTenantId(UUID tenantId);
}
