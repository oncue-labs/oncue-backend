package com.oncue.common.security;

import com.oncue.auth.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.jsonwebtoken.JwtException;

@Service
public class AccessTokenService {

    private final SecretKey signingKey;
    private final Duration tokenTtl;
    private final Clock clock;

    @Autowired
    public AccessTokenService(
            @Value("${oncue.auth.signing-secret}") String signingSecret,
            @Value("${oncue.auth.access-token-ttl:PT1H}") Duration tokenTtl) {
        this(signingSecret, tokenTtl, Clock.systemUTC());
    }

    public AccessTokenService(String signingSecret, Duration tokenTtl, Clock clock) {
        this.signingKey = Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8));
        this.tokenTtl = tokenTtl;
        this.clock = clock;
    }

    public com.oncue.common.security.AccessToken issue(User user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(tokenTtl);
        String token = Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new com.oncue.common.security.AccessToken(token, expiresAt);
    }

    public Optional<Long> userId(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.of(Long.valueOf(subject));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
