package com.oncue.call;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oncue.auth.model.User;
import com.oncue.combination.model.Persona;
import com.oncue.combination.model.Scenario;
import com.oncue.reservation.model.Reservation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CallSessionReconciliationSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-13T00:10:00Z");

    @Mock
    private CallSessionRepository callSessionRepository;

    @Mock
    private com.oncue.reservation.repository.ReservationRepository reservationRepository;

    @Mock
    private com.oncue.conversation.service.DialoguePolicyService dialoguePolicyService;

    @Mock
    private VoiceServerClient voiceServerClient;

    @Mock
    private CallSessionService callSessionService;

    @Test
    void runsDailyReconciliationAtTheCurrentUtcTime() {
        CallSessionReconciliationScheduler scheduler = new CallSessionReconciliationScheduler(
                callSessionService, Clock.fixed(NOW, ZoneOffset.UTC));

        scheduler.reconcileExpiredCallSessions();

        verify(callSessionService).reconcileExpiredCallSessions(NOW);
    }

    @Test
    void closesExpiredSessionsUsingTheirCurrentCallStatus() {
        CallSession preparing = session(1L, CallStatus.PREPARING);
        CallSession ringing = session(2L, CallStatus.RINGING);
        CallSession connecting = session(3L, CallStatus.CONNECTING);
        CallSession inCall = session(4L, CallStatus.IN_CALL);
        when(callSessionRepository.findUnfinishedBefore(
                Instant.parse("2026-09-13T00:03:00Z")))
                .thenReturn(List.of(preparing, ringing, connecting, inCall));

        CallSessionService service = new CallSessionService(
                reservationRepository,
                callSessionRepository,
                dialoguePolicyService,
                voiceServerClient,
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.reconcileExpiredCallSessions(NOW);

        org.assertj.core.api.Assertions.assertThat(preparing.getCallOutcome())
                .isEqualTo(CallOutcome.FAILED);
        org.assertj.core.api.Assertions.assertThat(ringing.getCallOutcome())
                .isEqualTo(CallOutcome.FAILED);
        org.assertj.core.api.Assertions.assertThat(connecting.getCallOutcome())
                .isEqualTo(CallOutcome.FAILED);
        org.assertj.core.api.Assertions.assertThat(inCall.getCallOutcome())
                .isEqualTo(CallOutcome.SUCCEEDED);
        verify(callSessionRepository, org.mockito.Mockito.times(4))
                .save(any(CallSession.class));
    }

    private static CallSession session(Long id, CallStatus status) {
        CallSession session = new CallSession(id, reservation());
        session.advanceTo(status);
        return session;
    }

    private static Reservation reservation() {
        return new Reservation(
                new User(7L),
                new Persona(11L, "santa", "Santa", "description", "context",
                        "instructions", List.of("persona rule"), "santa-default",
                        "/santa.png", "/santa.mp3", "ACTIVE"),
                new Scenario(21L, "child-roleplay", "Child roleplay", "description",
                        "context", "goal", "scenario instructions",
                        List.of("scenario rule"), "ACTIVE"),
                "context", "goal", Instant.parse("2026-09-13T00:00:00Z"), "UTC");
    }
}
