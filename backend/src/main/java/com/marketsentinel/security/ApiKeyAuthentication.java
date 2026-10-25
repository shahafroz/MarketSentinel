package com.marketsentinel.security;

import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** A broker system authenticated with an API key. It may only send market data (ROLE_INGEST). */
public class ApiKeyAuthentication extends AbstractAuthenticationToken {

    private final UUID tenantId;
    private final String keyName;

    public ApiKeyAuthentication(UUID tenantId, String keyName) {
        super(List.of(new SimpleGrantedAuthority("ROLE_INGEST")));
        this.tenantId = tenantId;
        this.keyName = keyName;
        setAuthenticated(true);
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String keyName() {
        return keyName;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return keyName;
    }
}
