package com.marketsentinel.security;

import com.marketsentinel.auth.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates broker systems on the ingest endpoints with the `X-API-Key` header. */
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final ApiKeyRepository keys;

    public ApiKeyFilter(ApiKeyRepository keys) {
        this.keys = keys;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String raw = request.getHeader(HEADER);
        if (raw != null && !raw.isBlank()) {
            keys.findByKeyHash(ApiKeys.hash(raw.trim()))
                    .filter(k -> k.isActive())
                    .ifPresent(k -> SecurityContextHolder.getContext()
                            .setAuthentication(new ApiKeyAuthentication(k.getTenantId(), k.getName())));
        }
        chain.doFilter(request, response);
    }
}
