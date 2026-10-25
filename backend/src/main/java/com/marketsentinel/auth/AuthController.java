package com.marketsentinel.auth;

import com.marketsentinel.audit.AuditService;
import com.marketsentinel.security.CurrentUser;
import com.marketsentinel.security.TokenService;
import com.marketsentinel.tenant.Tenant;
import com.marketsentinel.tenant.TenantRepository;
import com.marketsentinel.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository users;
    private final TenantRepository tenants;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final AuditService audit;

    public AuthController(UserRepository users, TenantRepository tenants, PasswordEncoder passwords, TokenService tokens,
            AuditService audit) {
        this.users = users;
        this.tenants = tenants;
        this.passwords = passwords;
        this.tokens = tokens;
        this.audit = audit;
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record SignupRequest(@NotBlank @Size(max = 120) String firmName, @NotBlank @Email String email,
            @NotBlank @Size(min = 10, max = 100) String password) {
    }

    public record UserView(UUID id, String email, Role role, UUID tenantId, String firmName, String plan) {
    }

    public record LoginResponse(String token, Instant expiresAt, UserView user) {
    }

    @PostMapping("/login")
    @Transactional
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AppUser user = users.findByEmail(request.email().toLowerCase())
                .filter(u -> passwords.matches(request.password(), u.getPasswordHash()))
                // Same message for "no such user" and "wrong password": do not reveal which emails exist.
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Wrong email or password"));
        audit.record(user.getTenantId(), user.getEmail(), "LOGIN", "user", user.getId(), "");
        return respond(user);
    }

    /** Self-service signup: creates a new firm on the Free plan with this person as its admin. */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public LoginResponse signup(@Valid @RequestBody SignupRequest request) {
        if (users.findByEmail(request.email().toLowerCase()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        Tenant tenant = tenants.save(new Tenant(request.firmName().trim()));
        AppUser admin = users.save(new AppUser(tenant.getId(), request.email(), passwords.encode(request.password()),
                Role.ADMIN));
        audit.record(tenant.getId(), admin.getEmail(), "FIRM_CREATED", "tenant", tenant.getId(), tenant.getName());
        return respond(admin);
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public UserView me() {
        CurrentUser current = CurrentUser.get();
        AppUser user = users.findByEmail(current.actor()).orElseThrow(() -> ApiException.notFound("User"));
        return view(user);
    }

    private LoginResponse respond(AppUser user) {
        TokenService.IssuedToken token = tokens.issue(user);
        return new LoginResponse(token.token(), token.expiresAt(), view(user));
    }

    private UserView view(AppUser user) {
        Tenant tenant = tenants.findById(user.getTenantId()).orElseThrow();
        return new UserView(user.getId(), user.getEmail(), user.getRole(), tenant.getId(), tenant.getName(),
                tenant.getPlan().name());
    }
}
