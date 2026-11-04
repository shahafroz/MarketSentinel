package com.marketsentinel.security;

import com.marketsentinel.config.SentinelProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP fixed-window rate limit (in memory). Login and signup get a tight limit against password
 * guessing; everything else a generous one. For several server instances this would move to Redis.
 *
 * The client IP comes from X-Forwarded-For only as far as our own proxies vouch for it: each proxy
 * appends the address it saw, so anything further left was written by the caller and can be forged
 * to dodge the limit. Set sentinel.trusted-proxy-hops to the number of proxies in front of the app.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;
    private static final int AUTH_LIMIT = 20;
    private static final int DEFAULT_LIMIT = 1_200;

    private record Window(long start, AtomicInteger count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int trustedProxyHops;

    public RateLimitFilter(SentinelProperties props) {
        this.trustedProxyHops = props.trustedProxyHops();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean auth = request.getRequestURI().startsWith("/api/v1/auth/");
        String key = (auth ? "auth:" : "api:")
                + clientIp(request.getHeader("X-Forwarded-For"), request.getRemoteAddr(), trustedProxyHops);
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, old) -> old == null || now - old.start() >= WINDOW_MS
                ? new Window(now, new AtomicInteger()) : old);
        int limit = auth ? AUTH_LIMIT : DEFAULT_LIMIT;
        if (w.count().incrementAndGet() > limit) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(Math.max(1, (w.start() + WINDOW_MS - now) / 1000)));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many requests, please wait a minute\"}");
            return;
        }
        if (windows.size() > 50_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().start() >= WINDOW_MS);
        }
        chain.doFilter(request, response);
    }

    static String clientIp(String forwardedFor, String remoteAddr, int trustedProxyHops) {
        if (trustedProxyHops <= 0 || forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddr;
        }
        String[] hops = forwardedFor.split(",");
        // Fewer entries than proxies means the request did not come through all of them.
        return hops.length < trustedProxyHops ? remoteAddr : hops[hops.length - trustedProxyHops].trim();
    }
}
