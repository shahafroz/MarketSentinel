package com.marketsentinel.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketsentinel.config.SentinelProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private static final int AUTH_LIMIT = 20;

    private static int login(RateLimitFilter filter, String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", forwardedFor);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    private static RateLimitFilter filter(int hops) {
        return new RateLimitFilter(new SentinelProperties(null, null, List.of(), hops, null, null, null, null, null, null));
    }

    @Test
    void forgedForwardedForCannotDodgeTheLoginLimit() throws Exception {
        RateLimitFilter filter = filter(1);
        for (int i = 0; i < AUTH_LIMIT; i++) {
            assertThat(login(filter, "1.2.3." + i + ", 203.0.113.9")).isEqualTo(200);
        }
        assertThat(login(filter, "9.9.9.9, 203.0.113.9")).isEqualTo(429);
    }

    @Test
    void differentRealClientsHaveSeparateLimits() throws Exception {
        RateLimitFilter filter = filter(1);
        for (int i = 0; i < AUTH_LIMIT; i++) {
            login(filter, "203.0.113.9");
        }
        assertThat(login(filter, "203.0.113.9")).isEqualTo(429);
        assertThat(login(filter, "203.0.113.10")).isEqualTo(200);
    }

    @Test
    void picksTheEntryAddedByOurOutermostProxy() {
        assertThat(RateLimitFilter.clientIp("6.6.6.6, 203.0.113.9, 10.1.1.1", "10.0.0.1", 2)).isEqualTo("203.0.113.9");
    }

    @Test
    void ignoresTheHeaderWhenThereAreNoProxies() {
        assertThat(RateLimitFilter.clientIp("6.6.6.6", "10.0.0.1", 0)).isEqualTo("10.0.0.1");
    }

    @Test
    void fallsBackToTheConnectionWhenTheHeaderIsMissingOrTooShort() {
        assertThat(RateLimitFilter.clientIp(null, "10.0.0.1", 1)).isEqualTo("10.0.0.1");
        assertThat(RateLimitFilter.clientIp("6.6.6.6", "10.0.0.1", 2)).isEqualTo("10.0.0.1");
    }
}
