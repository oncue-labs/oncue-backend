package com.oncue.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.oncue.conversation.model.DialoguePolicy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class RestVoiceServerClientTest {

    @Test
    void createsVoiceSessionWithNumericIdsAndBearerToken() {
        RestClient.Builder builder = builderWithIsoInstantSerialization();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestVoiceServerClient client = new RestVoiceServerClient(
                builder.build(), "https://voice.test", "service-token");

        server.expect(requestTo("https://voice.test/internal/v1/voice-sessions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "callSessionId": 321,
                          "userId": 7,
                          "policySnapshot": {
                            "role": "Santa",
                            "stages": ["greeting"],
                            "goal": "bedtime",
                            "allowedTopics": [],
                            "forbiddenTopics": ["passwords"],
                            "terminationConditions": ["goal reached"],
                            "language": "ko-KR",
                            "voiceId": "santa-default",
                            "instructions": ["Speak warmly"],
                            "dialogueRules": ["Do not ask for secrets"],
                            "scenarioContext": "child is getting ready for bed",
                            "voiceSettings": {}
                          },
                          "expiresAt": "2026-09-08T12:07:00Z"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "callSessionId": 321,
                          "voiceSessionId": "voice-session-321",
                          "createdAt": "2026-09-08T12:00:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        VoiceSessionResponse response = client.createSession(new CreateVoiceSessionRequest(
                321L,
                7L,
                policy(),
                Instant.parse("2026-09-08T12:07:00Z")));

        assertThat(response.callSessionId()).isEqualTo(321L);
        assertThat(response.voiceSessionId()).isEqualTo("voice-session-321");
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-09-08T12:00:00Z"));
        server.verify();
    }

    @Test
    void terminatesVoiceSessionAtExpectedPathWithBearerToken() {
        RestClient.Builder builder = builderWithIsoInstantSerialization();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestVoiceServerClient client = new RestVoiceServerClient(
                builder.build(), "https://voice.test", "service-token");

        server.expect(requestTo("https://voice.test/internal/v1/voice-sessions/voice-session-321/terminate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
                .andRespond(withNoContent());

        client.terminateSession("voice-session-321");

        server.verify();
    }

    private static DialoguePolicy policy() {
        return new DialoguePolicy("Santa", List.of("greeting"), "bedtime", List.of(),
                List.of("passwords"), List.of("goal reached"), "ko-KR", "santa-default",
                List.of("Speak warmly"), List.of("Do not ask for secrets"),
                "child is getting ready for bed", Map.of());
    }

    private static RestClient.Builder builderWithIsoInstantSerialization() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return RestClient.builder()
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(
                            new MappingJackson2HttpMessageConverter(objectMapper));
                });
    }
}
