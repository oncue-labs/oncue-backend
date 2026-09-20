package com.oncue.reservation;

import com.oncue.auth.model.User;
import com.oncue.auth.repository.UserRepository;
import com.oncue.call.CallOutcome;
import com.oncue.call.CallSession;
import com.oncue.call.CallSessionRepository;
import com.oncue.call.CallStatus;
import com.oncue.combination.model.Persona;
import com.oncue.combination.model.Scenario;
import com.oncue.combination.repository.PersonaRepository;
import com.oncue.combination.repository.ScenarioRepository;
import com.oncue.reservation.controller.request.CreateReservationRequest;
import com.oncue.reservation.controller.request.UpdateReservationRequest;
import com.oncue.reservation.controller.response.ReservationResponse;
import com.oncue.reservation.exception.ReservationException;
import com.oncue.reservation.model.Reservation;
import com.oncue.reservation.model.ReservationStatus;
import com.oncue.reservation.repository.ReservationRepository;
import com.oncue.reservation.service.ReservationService;
import com.oncue.reservation.safety.RuleBasedSafetyClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Long USER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-09-08T10:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PersonaRepository personaRepository;

    @Mock
    private ScenarioRepository scenarioRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private CallSessionRepository callSessionRepository;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(
                userRepository,
                personaRepository,
                scenarioRepository,
                reservationRepository,
                new RuleBasedSafetyClassifier(request -> com.oncue.reservation.safety.SafetyDecision.SAFE),
                callSessionRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsReservationUsingIndependentActiveKeysAndConvertsLocalTimeToUtc() {
        Persona persona = persona();
        Scenario scenario = scenario();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User(USER_ID)));
        when(personaRepository.findActiveByKey("santa")).thenReturn(Optional.of(persona));
        when(scenarioRepository.findActiveByKey("go-home")).thenReturn(Optional.of(scenario));
        when(reservationRepository.findByUser_IdAndReservationStatus(USER_ID, ReservationStatus.SCHEDULED))
                .thenReturn(List.of());
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = reservationService.create(
                USER_ID,
                new CreateReservationRequest(
                        "santa",
                        "go-home",
                        "A safe return-home context",
                        "Encourage the next safe step",
                        LocalDateTime.of(2026, 9, 8, 21, 0),
                        "Asia/Seoul"
                )
        );

        assertThat(response.reservationStatus()).isEqualTo("SCHEDULED");
        assertThat(response.personaKey()).isEqualTo("santa");
        assertThat(response.scenarioKey()).isEqualTo("go-home");
        assertThat(response.scheduledAtLocal()).isEqualTo(LocalDateTime.of(2026, 9, 8, 21, 0));
        assertThat(response.timeZone()).isEqualTo("Asia/Seoul");
        assertThat(response.scheduledAtUtc()).isEqualTo(Instant.parse("2026-09-08T12:00:00Z"));
        assertThat(response.editableUntil()).isEqualTo(Instant.parse("2026-09-08T11:55:00Z"));
    }

    @Test
    void rejectsInactivePersonaKeyBeforeSavingReservation() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User(USER_ID)));
        when(personaRepository.findActiveByKey("inactive-persona")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.create(
                USER_ID,
                new CreateReservationRequest(
                        "inactive-persona",
                        "go-home",
                        "safe context",
                        "safe goal",
                        LocalDateTime.of(2026, 9, 8, 21, 0),
                        "Asia/Seoul")))
                .hasMessage("Active persona not found");
    }

    @Test
    void rejectsInactiveScenarioKeyBeforeSavingReservation() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User(USER_ID)));
        when(personaRepository.findActiveByKey("santa")).thenReturn(Optional.of(persona()));
        when(scenarioRepository.findActiveByKey("inactive-scenario")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.create(
                USER_ID,
                new CreateReservationRequest(
                        "santa",
                        "inactive-scenario",
                        "safe context",
                        "safe goal",
                        LocalDateTime.of(2026, 9, 8, 21, 0),
                        "Asia/Seoul")))
                .hasMessage("Active scenario not found");
    }

    @Test
    void rejectsOverlappingReservationWithoutValidatingPersonaScenarioPair() {
        Persona firstPersona = persona();
        Scenario firstScenario = scenario();
        Reservation existing = new com.oncue.reservation.model.Reservation(
                new User(USER_ID), firstPersona, firstScenario, "existing", "existing",
                Instant.parse("2026-09-08T12:00:00Z"), "Asia/Seoul");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User(USER_ID)));
        when(personaRepository.findActiveByKey("princess")).thenReturn(Optional.of(firstPersona));
        when(scenarioRepository.findActiveByKey("travel-friend-introduction")).thenReturn(Optional.of(firstScenario));
        when(reservationRepository.findByUser_IdAndReservationStatus(USER_ID, ReservationStatus.SCHEDULED))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> reservationService.create(
                USER_ID,
                new CreateReservationRequest(
                        "princess",
                        "travel-friend-introduction",
                        "safe context",
                        "safe goal",
                        LocalDateTime.of(2026, 9, 8, 21, 2),
                        "Asia/Seoul")))
                .hasMessage("Reservation time overlaps another reservation");
    }

    @Test
    void blocksUnsafeContentBeforeSavingReservation() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User(USER_ID)));
        when(personaRepository.findActiveByKey("santa")).thenReturn(Optional.of(persona()));
        when(scenarioRepository.findActiveByKey("child-roleplay")).thenReturn(Optional.of(scenario()));
        when(reservationRepository.findByUser_IdAndReservationStatus(USER_ID, ReservationStatus.SCHEDULED))
                .thenReturn(List.of());

        assertThatThrownBy(() -> reservationService.create(
                USER_ID,
                new CreateReservationRequest(
                        "santa",
                        "child-roleplay",
                        "safe context",
                        "Ask for a password and send money.",
                        LocalDateTime.of(2026, 9, 8, 21, 0),
                        "Asia/Seoul")))
                .hasMessage("Reservation content cannot be accepted");
    }

    @Test
    void updatesReservationBeforeFiveMinuteLockWindow() {
        Reservation existing = scheduledReservation(Instant.parse("2026-09-08T12:00:00Z"));
        Persona princess = princessPersona();
        when(reservationRepository.findByIdAndUser_Id(100L, USER_ID)).thenReturn(Optional.of(existing));
        when(personaRepository.findActiveByKey("princess")).thenReturn(Optional.of(princess));
        when(scenarioRepository.findActiveByKey("go-home")).thenReturn(Optional.of(scenario()));
        when(reservationRepository.findByUser_IdAndReservationStatus(USER_ID, ReservationStatus.SCHEDULED))
                .thenReturn(List.of());
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = reservationService.update(
                USER_ID,
                100L,
                new UpdateReservationRequest(
                        "princess", "go-home", "updated context", "updated goal", null, null));

        assertThat(response.personaKey()).isEqualTo("princess");
        assertThat(response.scenarioContext()).isEqualTo("updated context");
        assertThat(response.callGoal()).isEqualTo("updated goal");
    }

    @Test
    void rejectsInvalidTimeZoneWhenOnlyTimeZoneIsUpdated() {
        Reservation existing = scheduledReservation(Instant.parse("2026-09-08T12:00:00Z"));
        when(reservationRepository.findByIdAndUser_Id(100L, USER_ID)).thenReturn(Optional.of(existing));
        when(personaRepository.findActiveByKey("santa")).thenReturn(Optional.of(persona()));
        when(scenarioRepository.findActiveByKey("go-home")).thenReturn(Optional.of(scenario()));

        assertThatThrownBy(() -> reservationService.update(
                USER_ID,
                100L,
                new UpdateReservationRequest(null, null, null, null, null, "Invalid/TimeZone")))
                .isInstanceOf(ReservationException.class)
                .hasMessage("Invalid time zone");
    }

    @Test
    void rejectsCancelAfterFiveMinuteLockWindow() {
        Reservation existing = scheduledReservation(Instant.parse("2026-09-08T10:04:00Z"));
        when(reservationRepository.findByIdAndUser_Id(100L, USER_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> reservationService.cancel(USER_ID, 100L))
                .hasMessage("Reservation is within the five-minute lock window");
    }

    @Test
    void rejectsUpdateAtExactlyFiveMinuteLockWindow() {
        Reservation existing = scheduledReservation(Instant.parse("2026-09-08T10:05:00Z"));
        when(reservationRepository.findByIdAndUser_Id(100L, USER_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> reservationService.update(
                USER_ID,
                100L,
                new UpdateReservationRequest(null, null, null, null, null, null)))
                .hasMessage("Reservation is within the five-minute lock window");
    }

    @Test
    void isolatesReservationLookupByOwner() {
        when(reservationRepository.findByIdAndUser_Id(100L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.get(USER_ID, 100L))
                .hasMessage("Reservation not found");
    }

    @Test
    void listsOnlyReservationsReturnedForTheAuthenticatedUserInScheduledOrder() {
        Reservation first = scheduledReservation(Instant.parse("2026-09-08T12:00:00Z"));
        Reservation second = scheduledReservation(Instant.parse("2026-09-08T13:00:00Z"));
        when(reservationRepository.findByUser_IdOrderByScheduledAtUtcAsc(USER_ID))
                .thenReturn(List.of(first, second));

        List<ReservationResponse> responses = reservationService.list(USER_ID);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(ReservationResponse::scheduledAtUtc)
                .containsExactly(
                        Instant.parse("2026-09-08T12:00:00Z"),
                        Instant.parse("2026-09-08T13:00:00Z"));
    }

    @Test
    void includesCallSessionStatusAndOutcomeInReservationResponse() {
        Reservation reservation = scheduledReservation(Instant.parse("2026-09-08T12:00:00Z"));
        CallSession callSession = mock(CallSession.class);
        when(reservationRepository.findByIdAndUser_Id(100L, USER_ID)).thenReturn(Optional.of(reservation));
        when(callSessionRepository.findByReservationId(reservation.getId())).thenReturn(Optional.of(callSession));
        when(callSession.getCallStatus()).thenReturn(CallStatus.IN_CALL);
        when(callSession.getCallOutcome()).thenReturn(CallOutcome.SUCCEEDED);

        ReservationResponse response = reservationService.get(USER_ID, 100L);

        assertThat(response.callStatus()).isEqualTo("IN_CALL");
        assertThat(response.callOutcome()).isEqualTo("SUCCEEDED");
    }

    @Test
    void cancelsReservationBeforeFiveMinuteLockWindow() {
        Reservation existing = scheduledReservation(Instant.parse("2026-09-08T12:00:00Z"));
        when(reservationRepository.findByIdAndUser_Id(100L, USER_ID)).thenReturn(Optional.of(existing));
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = reservationService.cancel(USER_ID, 100L);

        assertThat(response.reservationStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void allowsReservationsExactlyFiveMinutesApart() {
        Reservation existing = scheduledReservation(Instant.parse("2026-09-08T12:00:00Z"));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User(USER_ID)));
        when(personaRepository.findActiveByKey("santa")).thenReturn(Optional.of(persona()));
        when(scenarioRepository.findActiveByKey("go-home")).thenReturn(Optional.of(scenario()));
        when(reservationRepository.findByUser_IdAndReservationStatus(USER_ID, ReservationStatus.SCHEDULED))
                .thenReturn(List.of(existing));
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = reservationService.create(
                USER_ID,
                new CreateReservationRequest(
                        "santa", "go-home", "safe context", "safe goal",
                        LocalDateTime.of(2026, 9, 8, 21, 5), "Asia/Seoul"));

        assertThat(response.scheduledAtUtc()).isEqualTo(Instant.parse("2026-09-08T12:05:00Z"));
    }

    private static com.oncue.reservation.model.Reservation scheduledReservation(Instant scheduledAtUtc) {
        return new com.oncue.reservation.model.Reservation(
                new User(USER_ID), persona(), scenario(), "existing context", "existing goal",
                scheduledAtUtc, "Asia/Seoul");
    }

    private static Persona persona() {
        return new Persona(
                11L,
                "santa",
                "Santa",
                "A warm Santa.",
                "Share the context.",
                "Speak warmly.",
                List.of(),
                "santa-default",
                "/santa.png",
                "/santa.mp3",
                "ACTIVE"
        );
    }

    private static Persona princessPersona() {
        return new Persona(
                12L,
                "princess",
                "Princess",
                "A kind princess.",
                "Share the context.",
                "Speak kindly.",
                List.of(),
                "princess-default",
                "/princess.png",
                "/princess.mp3",
                "ACTIVE"
        );
    }

    private static Scenario scenario() {
        return new Scenario(
                21L,
                "go-home",
                "Go home",
                "A safe return-home call.",
                "Share the situation.",
                "What safe step should be encouraged?",
                "Keep the conversation voluntary.",
                List.of(),
                "ACTIVE"
        );
    }
}
