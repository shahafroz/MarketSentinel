package com.marketsentinel.security;

import com.marketsentinel.auth.AppUser;
import com.marketsentinel.config.SentinelProperties;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues signed JWTs after login. The token carries the user's email, firm (tenant) and role. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final Duration ttl;

    public TokenService(JwtEncoder encoder, SentinelProperties props) {
        this.encoder = encoder;
        this.ttl = Duration.ofMinutes(props.jwt().ttlMinutes());
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        Instant expires = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("market-sentinel")
                .subject(user.getEmail())
                .issuedAt(now)
                .expiresAt(expires)
                .claim("tenant", user.getTenantId().toString())
                .claim("role", user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expires);
    }
}
