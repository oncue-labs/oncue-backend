package com.oncue.call;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.oncue.auth.model.User;
import com.oncue.auth.repository.UserRepository;
import com.oncue.combination.model.Persona;
import com.oncue.combination.model.Scenario;
import com.oncue.combination.repository.PersonaRepository;
import com.oncue.combination.repository.ScenarioRepository;
import com.oncue.reservation.model.Reservation;
import com.oncue.reservation.repository.ReservationRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@EnableTransactionManagement
@Import(ConnectionTokenServiceJpaTest.ConnectionTokenServiceConfiguration.class)
class ConnectionTokenServiceJpaTest {

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    @Autowired
    private CallSessionRepository callSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PersonaRepository personaRepository;

    @Autowired
    private ScenarioRepository scenarioRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ConnectionTokenService connectionTokenService;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void issuesTokenAfterRepositoryReadReturnsDetachedCallSession() throws Exception {
        PersistedCallSession persistedCallSession = saveCallSession();

        assertThatCode(() -> connectionTokenService.issue(
                persistedCallSession.userId(),
                persistedCallSession.callSessionId()))
                .doesNotThrowAnyException();
    }

    private PersistedCallSession saveCallSession() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            User user = userRepository.saveAndFlush(User.active());
            Persona persona = personaRepository.saveAndFlush(new Persona(
                    null,
                    "test-persona",
                    "Test persona",
                    "Test persona description",
                    "Test context",
                    "Test instructions",
                    List.of(),
                    "test-voice",
                    null,
                    null,
                    Persona.ACTIVE_STATUS));
            Scenario scenario = scenarioRepository.saveAndFlush(new Scenario(
                    null,
                    "test-scenario",
                    "Test scenario",
                    "Test scenario description",
                    "Test context",
                    "Test goal",
                    "Test instructions",
                    List.of(),
                    Scenario.ACTIVE_STATUS));
            Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
                    user,
                    persona,
                    scenario,
                    "Test scenario context",
                    "Test call goal",
                    NOW.plusSeconds(300),
                    "UTC"));
            CallSession callSession = new CallSession(reservation);
            callSession.markVoiceSession("voice-session-1");
            callSession = callSessionRepository.saveAndFlush(callSession);
            return new PersistedCallSession(user.getId(), callSession.getId());
        });
    }

    private record PersistedCallSession(Long userId, Long callSessionId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConnectionTokenServiceConfiguration {

        @Bean
        ConnectionTokenService connectionTokenService(CallSessionRepository callSessionRepository)
                throws Exception {
            return new ConnectionTokenService(
                    callSessionRepository,
                    Files.readString(
                            Path.of(new ClassPathResource("test-connection-private-key.pem")
                                    .getURI()),
                            StandardCharsets.UTF_8),
                    "wss://voice.example.com/v1/signaling",
                    "",
                    "",
                    "",
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }
}
