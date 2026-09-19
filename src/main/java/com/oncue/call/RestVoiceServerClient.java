package com.oncue.call;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** HTTP adapter for the internal oncue-voice session API. */
@Component
public class RestVoiceServerClient implements VoiceServerClient {

    private final RestClient restClient;
    private final String baseUrl;
    private final String serviceToken;

    @Autowired
    public RestVoiceServerClient(
            RestClient.Builder restClientBuilder,
            @Value("${oncue.voice.base-url:http://localhost:8000}") String baseUrl,
            @Value("${oncue.voice.service-token:}") String serviceToken) {
        this(restClientBuilder.build(), baseUrl, serviceToken);
    }

    RestVoiceServerClient(RestClient restClient, String baseUrl, String serviceToken) {
        this.restClient = restClient;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.serviceToken = serviceToken;
    }

    @Override
    public VoiceSessionResponse createSession(CreateVoiceSessionRequest request) {
        VoiceSessionResponse response = restClient.post()
                .uri(baseUrl + "/internal/v1/voice-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(serviceToken))
                .body(request)
                .retrieve()
                .body(VoiceSessionResponse.class);
        return Objects.requireNonNull(response, "Voice server response is empty");
    }

    @Override
    public void terminateSession(String voiceSessionId) {
        try {
            restClient.post()
                    .uri(baseUrl + "/internal/v1/voice-sessions/{voiceSessionId}/terminate", voiceSessionId)
                    .headers(headers -> headers.setBearerAuth(serviceToken))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ignored) {
            // The technical voice session may have already expired or been removed.
            // Termination is idempotent cleanup, so the caller can create a new session.
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Voice server base URL is required");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
