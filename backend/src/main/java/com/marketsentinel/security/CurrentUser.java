package com.marketsentinel.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Who is calling and which firm (tenant) they belong to. Every service reads the tenant from here,
 * never from the request body, so a user can never act on another firm's data.
 */
public record CurrentUser(UUID tenantId, String actor) {

    public static CurrentUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof ApiKeyAuthentication key) {
            return new CurrentUser(key.tenantId(), "api-key:" + key.keyName());
        }
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return new CurrentUser(UUID.fromString(jwt.getClaimAsString("tenant")), jwt.getSubject());
        }
        throw new IllegalStateException("No authenticated user");
    }
}
