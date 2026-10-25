package com.marketsentinel.auth;

import com.marketsentinel.audit.AuditService;
import com.marketsentinel.security.ApiKeys;
import com.marketsentinel.security.CurrentUser;
import com.marketsentinel.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Firm administration: team members and API keys. Admins only. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository users;
    private final ApiKeyRepository keys;
    private final PasswordEncoder passwords;
    private final AuditService audit;

    public AdminController(UserRepository users, ApiKeyRepository keys, PasswordEncoder passwords, AuditService audit) {
        this.users = users;
        this.keys = keys;
        this.passwords = passwords;
        this.audit = audit;
    }

    public record NewUserRequest(@NotBlank @Email String email, @NotBlank @Size(min = 10, max = 100) String password,
            @NotNull Role role) {
    }

    public record UserRow(UUID id, String email, Role role, Instant createdAt) {
    }

    public record NewKeyRequest(@NotBlank @Size(max = 80) String name) {
    }

    public record KeyRow(UUID id, String name, String prefix, Instant createdAt, Instant revokedAt) {
    }

    /** The full key is returned exactly once, at creation. It cannot be shown again. */
    public record CreatedKey(UUID id, String name, String apiKey) {
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public List<UserRow> listUsers() {
        return users.findByTenantIdOrderByEmail(CurrentUser.get().tenantId()).stream()
                .map(u -> new UserRow(u.getId(), u.getEmail(), u.getRole(), u.getCreatedAt())).toList();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UserRow createUser(@Valid @RequestBody NewUserRequest request) {
        CurrentUser me = CurrentUser.get();
        if (users.findByEmail(request.email().toLowerCase()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        AppUser user = users.save(new AppUser(me.tenantId(), request.email(), passwords.encode(request.password()),
                request.role()));
        audit.record(me.tenantId(), me.actor(), "USER_CREATED", "user", user.getId(), user.getEmail() + " as " + user.getRole());
        return new UserRow(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }

    @GetMapping("/api-keys")
    @Transactional(readOnly = true)
    public List<KeyRow> listKeys() {
        return keys.findByTenantIdOrderByCreatedAtDesc(CurrentUser.get().tenantId()).stream()
                .map(k -> new KeyRow(k.getId(), k.getName(), k.getPrefix(), k.getCreatedAt(), k.getRevokedAt())).toList();
    }

    @PostMapping("/api-keys")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CreatedKey createKey(@Valid @RequestBody NewKeyRequest request) {
        CurrentUser me = CurrentUser.get();
        String raw = ApiKeys.generate();
        ApiKey key = keys.save(new ApiKey(me.tenantId(), request.name().trim(), ApiKeys.prefix(raw), ApiKeys.hash(raw)));
        audit.record(me.tenantId(), me.actor(), "API_KEY_CREATED", "api_key", key.getId(), key.getName());
        return new CreatedKey(key.getId(), key.getName(), raw);
    }

    @DeleteMapping("/api-keys/{id}")
    @Transactional
    public KeyRow revokeKey(@PathVariable UUID id) {
        CurrentUser me = CurrentUser.get();
        ApiKey key = keys.findByIdAndTenantId(id, me.tenantId()).orElseThrow(() -> ApiException.notFound("API key"));
        key.revoke();
        audit.record(me.tenantId(), me.actor(), "API_KEY_REVOKED", "api_key", key.getId(), key.getName());
        return new KeyRow(key.getId(), key.getName(), key.getPrefix(), key.getCreatedAt(), key.getRevokedAt());
    }
}
