package com.oncue.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncue.auth.model.User;
import com.oncue.auth.repository.UserRepository;
import com.oncue.call.CallOutcome;
import com.oncue.call.CallSession;
import com.oncue.call.CallSessionRepository;
import com.oncue.call.CallSessionResultRequest;
import com.oncue.call.CallStatus;
import com.oncue.call.CreateVoiceSessionRequest;
import com.oncue.call.VoiceServerClient;
import com.oncue.call.VoiceSessionResponse;
import com.oncue.common.security.AccessTokenService;
import com.oncue.reservation.controller.request.CreateReservationRequest;
import com.oncue.reservation.scheduler.ReservationPreparationScheduler;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ReservationToCallIntegrationTest.FakeVoiceServerConfiguration.class)
class ReservationToCallIntegrationTest {

    private static final String SERVICE_TOKEN = "integration-service-token";
    private static final String ACCESS_TOKEN_SIGNING_SECRET =
            "integration-test-access-token-signing-secret-which-is-long-enough";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("oncue")
            .withUsername("oncue")
            .withPassword("oncue");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CallSessionRepository callSessionRepository;

    @Autowired
    private ReservationPreparationScheduler reservationPreparationScheduler;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RecordingVoiceServerClient voiceServerClient;

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("oncue.auth.signing-secret", () -> ACCESS_TOKEN_SIGNING_SECRET);
        registry.add("oncue.voice.service-token", () -> SERVICE_TOKEN);
        registry.add("oncue.voice.jwt-private-key-file", () -> "src/test/resources/test-connection-private-key.pem");
    }

    @Test
    void completesReservationToCallFlowAcrossHttpDatabaseRedisAndVoiceBoundary() throws Exception {
        User user = userRepository.saveAndFlush(User.active());
        String accessToken = accessTokenService.issue(user).value();
        LocalDateTime scheduledAtLocal = LocalDateTime.now(ZoneOffset.UTC)
                .plusMinutes(2)
                .withNano(0);
        String scenarioContext = "아이 이름은 민수이고 오늘은 조금 늦게 잠자리에 들 상황이야.";
        String callGoal = "민수가 편안하게 잠들 준비를 하도록 도와줘.";

        String reservationResponse = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReservationRequest(
                                "santa",
                                "child-roleplay",
                                scenarioContext,
                                callGoal,
                                scheduledAtLocal,
                                "UTC"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long reservationId = objectMapper.readTree(reservationResponse)
                .path("reservationId")
                .asLong();

        String redisKey = "integration:" + UUID.randomUUID();
        redisTemplate.opsForValue().set(redisKey, "reservation-created");
        assertThat(redisTemplate.opsForValue().get(redisKey)).isEqualTo("reservation-created");

        reservationPreparationScheduler.prepareDueReservations();

        CallSession callSession = callSessionRepository.findByReservationId(reservationId)
                .orElseThrow();
        assertThat(callSession.getCallStatus()).isEqualTo(CallStatus.PREPARING);
        assertThat(callSession.getCallOutcome()).isNull();
        assertThat(callSession.getVoiceSessionId()).isEqualTo("voice-session-1");
        assertThat(voiceServerClient.lastRequest.callSessionId()).isEqualTo(callSession.getId());
        assertThat(voiceServerClient.lastRequest.userId()).isEqualTo(user.getId());
        assertThat(voiceServerClient.lastRequest.policySnapshot().scenarioContext())
                .isEqualTo(scenarioContext);
        assertThat(voiceServerClient.lastRequest.policySnapshot().goal()).isEqualTo(callGoal);
        assertThat(voiceServerClient.lastRequest.policySnapshot().role()).isEqualTo("Santa");

        String connectionTokenResponse = mockMvc.perform(post(
                        "/api/v1/call-sessions/{callSessionId}/connection-token", callSession.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode connectionToken = objectMapper.readTree(connectionTokenResponse);
        assertThat(connectionToken.path("connectionToken").asText()).isNotBlank();
        assertThat(connectionToken.path("signalingUrl").asText())
                .isEqualTo("ws://localhost:8000/v1/signaling/call-sessions/" + callSession.getId());
        assertThat(connectionToken.path("createdAt").asText()).isNotBlank();
        assertThat(connectionToken.path("expiresAt").asText()).isNotBlank();

        Instant startedAt = Instant.now().minusSeconds(30);
        Instant endedAt = Instant.now();
        mockMvc.perform(post("/internal/v1/call-sessions/{callSessionId}/result", callSession.getId())
                        .header("Authorization", "Bearer " + SERVICE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CallSessionResultRequest(
                                "voice-session-1",
                                CallStatus.IN_CALL,
                                CallOutcome.SUCCEEDED,
                                startedAt,
                                endedAt))))
                .andExpect(status().isNoContent());

        CallSession completedCallSession = callSessionRepository.findById(callSession.getId())
                .orElseThrow();
        assertThat(completedCallSession.getCallStatus()).isEqualTo(CallStatus.IN_CALL);
        assertThat(completedCallSession.getCallOutcome()).isEqualTo(CallOutcome.SUCCEEDED);
        assertThat(completedCallSession.getStartedAt()).isEqualTo(startedAt);
        assertThat(completedCallSession.getEndedAt()).isEqualTo(endedAt);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeVoiceServerConfiguration {

        @Bean
        @Primary
        RecordingVoiceServerClient voiceServerClient() {
            return new RecordingVoiceServerClient();
        }
    }

    static class RecordingVoiceServerClient implements VoiceServerClient {

        private CreateVoiceSessionRequest lastRequest;

        @Override
        public VoiceSessionResponse createSession(CreateVoiceSessionRequest request) {
            lastRequest = request;
            return new VoiceSessionResponse(request.callSessionId(), "voice-session-1", Instant.now());
        }

        @Override
        public void terminateSession(String voiceSessionId) {
        }
    }
}
