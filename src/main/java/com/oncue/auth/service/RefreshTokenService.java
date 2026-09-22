package com.oncue.auth.service;

import com.oncue.auth.model.RefreshTokenEntity;
import com.oncue.auth.model.User;
import com.oncue.auth.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration tokenTtl;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${oncue.auth.refresh-token-ttl:720h}") Duration tokenTtl) {
        this(refreshTokenRepository, tokenTtl, Clock.systemUTC());
    }

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, Duration tokenTtl, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenTtl = tokenTtl;
        this.clock = clock;
    }

    @Transactional
    public IssuedRefreshToken issue(User user) {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant expiresAt = clock.instant().plus(tokenTtl);
        refreshTokenRepository.save(new RefreshTokenEntity(user, hash(value), expiresAt));
        return new IssuedRefreshToken(value, expiresAt);
    }

    @Transactional
    public User consume(String value) {
        RefreshTokenEntity token = refreshTokenRepository
                .findByTokenHashAndRevokedAtIsNull(hash(value))
                .orElseThrow(() -> new IllegalArgumentException("Refresh token is invalid"));
        Instant now = clock.instant();
        if (!token.getExpiresAt().isAfter(now)) {
            token.revoke(now);
            throw new IllegalArgumentException("Refresh token is expired");
        }
        token.revoke(now);
        return token.getUser();
    }

    @Transactional
    public void revoke(String value) {
        refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(hash(value))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
