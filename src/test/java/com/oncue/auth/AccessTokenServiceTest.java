package com.oncue.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.oncue.auth.model.User;
import com.oncue.common.security.AccessToken;
import com.oncue.common.security.AccessTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class AccessTokenServiceTest {

    private static final String SIGNING_SECRET = "oncue-test-signing-secret-with-at-least-256-bits";
    private static final Instant ISSUED_AT = Instant.parse("2026-09-12T13:00:00Z");

    @Test
    void issuesTokenForUserWithConfiguredExpiration() {
        var service = new AccessTokenService(
                SIGNING_SECRET,
                Duration.ofHours(1),
                Clock.fixed(ISSUED_AT, ZoneOffset.UTC));

        AccessToken accessToken = service.issue(new User(42L));

        assertThat(accessToken.value()).isNotBlank();
        assertThat(accessToken.expiresAt()).isEqualTo(ISSUED_AT.plus(Duration.ofHours(1)));

        SecretKey signingKey = Keys.hmacShaKeyFor(SIGNING_SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().clock(() -> Date.from(ISSUED_AT)).verifyWith(signingKey).build()
                .parseSignedClaims(accessToken.value())
                .getPayload();
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getIssuedAt()).isEqualTo(Date.from(ISSUED_AT));
        assertThat(claims.getExpiration()).isEqualTo(Date.from(accessToken.expiresAt()));
    }
}
