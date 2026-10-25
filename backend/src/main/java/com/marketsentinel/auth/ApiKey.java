package com.marketsentinel.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A key a broker's systems use to send trades. Only its SHA-256 hash is stored, never the key. */
@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String prefix;

    @Column(nullable = false, unique = true)
    private String keyHash;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant revokedAt;

    protected ApiKey() {
    }

    public ApiKey(UUID tenantId, String name, String prefix, String keyHash) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.name = name;
        this.prefix = prefix;
        this.keyHash = keyHash;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getPrefix() { return prefix; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }

    public boolean isActive() {
        return revokedAt == null;
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }
}
