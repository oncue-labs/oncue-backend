package com.oncue.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import com.oncue.auth.model.RefreshTokenEntity;
import com.oncue.auth.model.User;
import com.oncue.auth.repository.RefreshTokenRepository;
import com.oncue.auth.service.RefreshTokenService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void rotatesAnUnexpiredRefreshTokenAndReturnsItsUser() {
        var entity = new RefreshTokenEntity(
                new User(42L),
                "hashed-refresh-token",
                NOW.plus(Duration.ofDays(30)));
        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(anyString()))
                .thenReturn(Optional.of(entity));

        var service = new RefreshTokenService(
                refreshTokenRepository,
                Duration.ofDays(30),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.consume("refresh-token").getId()).isEqualTo(42L);
        assertThat(entity.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsAnExpiredRefreshToken() {
        var entity = new RefreshTokenEntity(
                new User(42L),
                "hashed-refresh-token",
                NOW.minusSeconds(1));
        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(anyString()))
                .thenReturn(Optional.of(entity));

        var service = new RefreshTokenService(
                refreshTokenRepository,
                Duration.ofDays(30),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.consume("refresh-token"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(entity.getRevokedAt()).isEqualTo(NOW);
    }
}
