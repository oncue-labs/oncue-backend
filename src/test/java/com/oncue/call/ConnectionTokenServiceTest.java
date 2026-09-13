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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectionTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T00:00:00Z");
    private static final KeyPair SIGNING_KEY_PAIR = generateSigningKeyPair();
    private static final String PRIVATE_KEY_PEM = toPem(SIGNING_KEY_PAIR.getPrivate());

    private CallSessionRepository callSessionRepository;
    private CallSession callSession;
    private ConnectionTokenService service;

    @TempDir
    private Path temporaryDirectory;

    @BeforeEach
    void setUp() {
        callSessionRepository = mock(CallSessionRepository.class);
        callSession = callSession(42L, 7L, "voice-session-1");
        when(callSessionRepository.findById(42L)).thenReturn(Optional.of(callSession));
        service = new ConnectionTokenService(
                callSessionRepository,
                PRIVATE_KEY_PEM,
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

        var parsedToken = Jwts.parser()
                .verifyWith(SIGNING_KEY_PAIR.getPublic())
                .clock(() -> Date.from(NOW))
                .build()
                .parseSignedClaims(response.connectionToken());
        assertThat(parsedToken.getHeader().getAlgorithm()).isEqualTo("RS256");
        var claims = parsedToken.getPayload();
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
                PRIVATE_KEY_PEM,
                "wss://voice.example.com/v1/signaling",
                "",
                "",
                "",
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(serviceWithoutIceServers.issue(7L, 42L).iceServers()).isEmpty();
    }

    @Test
    void loadsSigningKeyFromConfiguredFileWhenInlineKeyIsAbsent() throws Exception {
        Path keyFile = temporaryDirectory.resolve("voice-jwt-private-key.pem");
        Files.writeString(keyFile, PRIVATE_KEY_PEM);
        ConnectionTokenService serviceFromFile = new ConnectionTokenService(
                callSessionRepository,
                "",
                keyFile.toString(),
                "wss://voice.example.com/v1/signaling",
                "",
                "",
                "",
                Clock.fixed(NOW, ZoneOffset.UTC));

        String token = serviceFromFile.issue(7L, 42L).connectionToken();

        assertThat(Jwts.parser()
                .verifyWith(SIGNING_KEY_PAIR.getPublic())
                .clock(() -> Date.from(NOW))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getId()).isNotBlank();
    }

    private static KeyPair generateSigningKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create test RSA key pair", exception);
        }
    }

    private static String toPem(PrivateKey privateKey) {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----";
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
