package com.oncue.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncue.common.security.EcPrivateKeyLoader;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** HTTP/2 adapter for Apple's VoIP APNs endpoint. */
@Component
public class ApnsPushNotificationSender implements PushNotificationSender {

    private static final String SANDBOX_ENDPOINT = "https://api.sandbox.push.apple.com";
    private static final String PRODUCTION_ENDPOINT = "https://api.push.apple.com";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String keyId;
    private final String teamId;
    private final String bundleId;
    private final PrivateKey privateKey;

    @Autowired
    public ApnsPushNotificationSender(
            ObjectMapper objectMapper,
            @Value("${oncue.push.apns.key-id:}") String keyId,
            @Value("${oncue.push.apns.team-id:}") String teamId,
            @Value("${oncue.push.apns.bundle-id:com.oncue.oncueMobile}") String bundleId,
            @Value("${oncue.push.apns.private-key:}") String keyMaterial,
            @Value("${oncue.push.apns.private-key-file:}") String keyFile) {
        this(HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_2)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                objectMapper, keyId, teamId, bundleId, keyMaterial, keyFile);
    }

    ApnsPushNotificationSender(
            HttpClient httpClient,
            String keyId,
            String teamId,
            String bundleId,
            String keyMaterial,
            String keyFile) {
        this(httpClient, new ObjectMapper(), keyId, teamId, bundleId, keyMaterial, keyFile);
    }

    private ApnsPushNotificationSender(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String keyId,
            String teamId,
            String bundleId,
            String keyMaterial,
            String keyFile) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.keyId = keyId;
        this.teamId = teamId;
        this.bundleId = bundleId;
        this.privateKey = isConfigured(keyId, teamId, bundleId, keyMaterial, keyFile)
                ? EcPrivateKeyLoader.load(keyMaterial, keyFile)
                : null;
    }

    @Override
    public void send(PushDevice device, IncomingCallPushPayload payload) {
        if (privateKey == null) {
            throw new IllegalStateException("APNs is not configured");
        }
        if (device.getPlatform() != PushPlatform.IOS) {
            throw new IllegalArgumentException("Only iOS APNs devices are supported");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint(device.getEnvironment()) + "/3/device/" + device.getDeviceToken()))
                .timeout(Duration.ofSeconds(10))
                .header("authorization", "bearer " + createProviderToken())
                .header("apns-topic", bundleId + ".voip")
                .header("apns-push-type", "voip")
                .header("apns-priority", "10")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(payload)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "APNs rejected the push with status " + response.statusCode()
                                + " and response " + response.body());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("APNs push request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("APNs push request was interrupted", exception);
        }
    }

    private String createProviderToken() {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .header()
                .keyId(keyId)
                .and()
                .issuer(teamId)
                .issuedAt(java.util.Date.from(issuedAt))
                .signWith(privateKey, Jwts.SIG.ES256)
                .compact();
    }

    private String toJson(IncomingCallPushPayload payload) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "aps", Map.of("content-available", 1),
                    "callSessionId", payload.callSessionId(),
                    "displayName", payload.displayName()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize APNs payload", exception);
        }
    }

    private static String endpoint(PushEnvironment environment) {
        return environment == PushEnvironment.SANDBOX
                ? SANDBOX_ENDPOINT
                : PRODUCTION_ENDPOINT;
    }

    private static boolean isConfigured(
            String keyId,
            String teamId,
            String bundleId,
            String keyMaterial,
            String keyFile) {
        return !isBlank(keyId) && !isBlank(teamId) && !isBlank(bundleId)
                && (!isBlank(keyMaterial) || !isBlank(keyFile));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
