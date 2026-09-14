package com.oncue.call;

import com.oncue.common.security.RsaPrivateKeyLoader;
import com.oncue.reservation.exception.ReservationException;
import io.jsonwebtoken.Jwts;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConnectionTokenService {

    private static final Duration CONNECTION_TOKEN_TTL = Duration.ofMinutes(1);

    private final CallSessionRepository callSessionRepository;
    private final PrivateKey signingKey;
    private final String signalingBaseUrl;
    private final List<String> iceServerUrls;
    private final String iceServerUsername;
    private final String iceServerCredential;
    private final Clock clock;

    @Autowired
    public ConnectionTokenService(
            CallSessionRepository callSessionRepository,
            @Value("${oncue.voice.jwt-private-key:}") String signingKeyMaterial,
            @Value("${oncue.voice.jwt-private-key-file:}") String signingKeyFile,
            @Value("${oncue.voice.signaling-url:ws://localhost:8000/v1/signaling}") String signalingBaseUrl,
            @Value("${oncue.voice.ice-servers.urls:}") String iceServerUrls,
            @Value("${oncue.voice.ice-servers.username:}") String iceServerUsername,
            @Value("${oncue.voice.ice-servers.credential:}") String iceServerCredential) {
        this(callSessionRepository, signingKeyMaterial, signingKeyFile, signalingBaseUrl, iceServerUrls,
                iceServerUsername, iceServerCredential, Clock.systemUTC());
    }

    ConnectionTokenService(
            CallSessionRepository callSessionRepository,
            String signingKeyMaterial,
            String signalingBaseUrl,
            String iceServerUrls,
            String iceServerUsername,
            String iceServerCredential,
            Clock clock) {
        this(callSessionRepository, signingKeyMaterial, "", signalingBaseUrl, iceServerUrls,
                iceServerUsername, iceServerCredential, clock);
    }

    ConnectionTokenService(
            CallSessionRepository callSessionRepository,
            String signingKeyMaterial,
            String signingKeyFile,
            String signalingBaseUrl,
            String iceServerUrls,
            String iceServerUsername,
            String iceServerCredential,
            Clock clock) {
        this.callSessionRepository = callSessionRepository;
        this.signingKey = RsaPrivateKeyLoader.load(signingKeyMaterial, signingKeyFile);
        this.signalingBaseUrl = trimTrailingSlash(signalingBaseUrl);
        this.iceServerUrls = Arrays.stream(iceServerUrls.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        this.iceServerUsername = blankToNull(iceServerUsername);
        this.iceServerCredential = blankToNull(iceServerCredential);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ConnectionTokenResponse issue(Long userId, Long callSessionId) {
        CallSession callSession = callSessionRepository.findById(callSessionId)
                .orElseThrow(() -> new ReservationException(HttpStatus.NOT_FOUND, "Call session not found"));
        if (!userId.equals(callSession.getReservation().getUser().getId())) {
            throw new ReservationException(HttpStatus.FORBIDDEN, "Call session does not belong to user");
        }
        if (callSession.isEnded() || callSession.getVoiceSessionId() == null) {
            throw new ReservationException(HttpStatus.CONFLICT, "Call session is not connectable");
        }

        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(CONNECTION_TOKEN_TTL);
        String token = Jwts.builder()
                .claim("callSessionId", callSessionId)
                .claim("userId", userId)
                .claim("scope", List.of("voice:connect"))
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(createdAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.RS256)
                .compact();

        List<ConnectionTokenResponse.IceServer> iceServers = iceServerUrls.isEmpty()
                ? List.of()
                : List.of(new ConnectionTokenResponse.IceServer(
                        iceServerUrls, iceServerUsername, iceServerCredential));

        return new ConnectionTokenResponse(
                token,
                signalingBaseUrl + "/call-sessions/" + callSessionId,
                iceServers,
                expiresAt,
                createdAt);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Signaling URL is required");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
