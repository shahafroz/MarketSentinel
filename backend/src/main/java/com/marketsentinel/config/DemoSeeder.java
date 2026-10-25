package com.marketsentinel.config;

import com.marketsentinel.audit.AuditService;
import com.marketsentinel.auth.AppUser;
import com.marketsentinel.auth.Role;
import com.marketsentinel.auth.UserRepository;
import com.marketsentinel.tenant.Plan;
import com.marketsentinel.tenant.Tenant;
import com.marketsentinel.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a demo firm with one user per role (analyst@demo.marketsentinel.dev and so on) the first
 * time the app starts, so the dashboard can be tried right away. Turn off with SEED_DEMO=false.
 */
@Component
public class DemoSeeder implements ApplicationRunner {

    public static final String FIRM = "Demo Brokerage LLC";
    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final SentinelProperties props;
    private final TenantRepository tenants;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AuditService audit;

    public DemoSeeder(SentinelProperties props, TenantRepository tenants, UserRepository users, PasswordEncoder passwords,
            AuditService audit) {
        this.props = props;
        this.tenants = tenants;
        this.users = users;
        this.passwords = passwords;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.demo().seed() || tenants.findByName(FIRM).isPresent()) {
            return;
        }
        Tenant tenant = new Tenant(FIRM);
        tenant.setPlan(Plan.PRO);
        tenants.save(tenant);
        String hash = passwords.encode(props.demo().password());
        for (Role role : Role.values()) {
            String email = role.name().toLowerCase() + "@demo.marketsentinel.dev";
            users.save(new AppUser(tenant.getId(), email, hash, role));
        }
        audit.record(tenant.getId(), "system", "DEMO_SEEDED", "tenant", tenant.getId(), "4 demo users");
        log.info("Demo firm created: sign in as analyst@demo.marketsentinel.dev (and supervisor@, auditor@, admin@)");
    }
}
