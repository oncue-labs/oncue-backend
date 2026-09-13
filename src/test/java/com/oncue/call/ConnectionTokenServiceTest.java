package com.oncue.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oncue.auth.model.User;
import com.oncue.combination.model.Persona;
import com.oncue.combination.model.Scenario;
import com.oncue.reservation.exception.ReservationException;
import com.oncue.reservation.model.Reservation;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConnectionTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T00:00:00Z");
    private static final String SIGNING_SECRET = "test-signing-secret-that-is-at-least-32-bytes-long";

    private CallSessionRepository callSessionRepository;
    private CallSession callSession;
    private ConnectionTokenService service;

    @BeforeEach
    void setUp() {
        callSessionRepository = mock(CallSessionRepository.class);
        callSession = callSession(42L, 7L, "voice-session-1");
        when(callSessionRepository.findById(42L)).thenReturn(Optional.of(callSession));
        service = new ConnectionTokenService(
                callSessionRepository,
                SIGNING_SECRET,
                "wss://voice.example.com/v1/signaling/",
                "stun:stun.example.com, turn:turn.example.com",
                "turn-user",
                "turn-credential",
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issuesOneMinuteJwtAndConnectionInformationForOwnedActiveCallSession() {
        ConnectionTokenResponse response = service.issue(7L, 42L);

        assertThat(response.connectionToken()).isNotBlank();
        assertThat(response.signalingUrl()).isEqualTo("wss://voice.example.com/v1/signaling/call-sessions/42");
        assertThat(response.iceServers()).containsExactly(new ConnectionTokenResponse.IceServer(
                java.util.List.of("stun:stun.example.com", "turn:turn.example.com"),
                "turn-user",
                "turn-credential"));
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(60));

        var claims = Jwts.parser()
                .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        SIGNING_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .clock(() -> Date.from(NOW))
                .build()
                .parseSignedClaims(response.connectionToken())
                .getPayload();
        assertThat(claims.get("callSessionId", Long.class)).isEqualTo(42L);
        assertThat(claims.get("userId", Long.class)).isEqualTo(7L);
        assertThat(claims.get("scope", java.util.List.class)).containsExactly("voice:connect");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void rejectsCallSessionOwnedByAnotherUser() {
        assertThatThrownBy(() -> service.issue(8L, 42L))
                .isInstanceOf(ReservationException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsEndedCallSession() {
        callSession.advanceTo(CallStatus.RINGING);
        callSession.complete(CallOutcome.SUCCEEDED, NOW, NOW.plusSeconds(10));

        assertThatThrownBy(() -> service.issue(7L, 42L))
                .isInstanceOf(ReservationException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void returnsNoIceServersWhenIceServerUrlsAreNotConfigured() {
        ConnectionTokenService serviceWithoutIceServers = new ConnectionTokenService(
                callSessionRepository,
                SIGNING_SECRET,
                "wss://voice.example.com/v1/signaling",
                "",
                "",
                "",
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(serviceWithoutIceServers.issue(7L, 42L).iceServers()).isEmpty();
    }

    private static CallSession callSession(Long callSessionId, Long userId, String voiceSessionId) {
        User user = new User(userId);
        Reservation reservation = new Reservation(
                user,
                mock(Persona.class),
                mock(Scenario.class),
                "context",
                "goal",
                NOW.plusSeconds(300),
                "Asia/Seoul");
        CallSession callSession = new CallSession(callSessionId, reservation);
        callSession.markVoiceSession(voiceSessionId);
        return callSession;
    }
}
