package com.oncue.call;

import com.oncue.auth.model.User;
import com.oncue.combination.model.Persona;
import com.oncue.combination.model.Scenario;
import com.oncue.conversation.model.DialoguePolicy;
import com.oncue.conversation.service.DialoguePolicyService;
import com.oncue.reservation.model.Reservation;
import com.oncue.reservation.exception.ReservationException;
import com.oncue.reservation.repository.ReservationRepository;
import com.oncue.push.IncomingCallPushPayload;
import com.oncue.push.PushNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T11:58:00Z");

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private CallSessionRepository callSessionRepository;

    @Mock
    private DialoguePolicyService dialoguePolicyService;

    @Mock
    private VoiceServerClient voiceServerClient;

    @Mock
    private PushNotificationService pushNotificationService;

    @Test
    void preparesDueReservationOnceAndStoresVoiceSessionId() {
        Reservation reservation = reservation();
        DialoguePolicy policy = policy();
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
        when(callSessionRepository.findByReservationId(100L)).thenReturn(Optional.empty());
        when(callSessionRepository.save(any(CallSession.class)))
                .thenAnswer(invocation -> {
                    CallSession callSession = invocation.getArgument(0);
                    return callSession.getId() == null
                            ? new CallSession(321L, callSession.getReservation())
                            : callSession;
                });
        when(dialoguePolicyService.build(
                reservation.getPersona(), reservation.getScenario(),
                reservation.getScenarioContext(), reservation.getCallGoal(),
                java.util.Locale.KOREAN)).thenReturn(policy);
        when(voiceServerClient.createSession(any(CreateVoiceSessionRequest.class)))
                .thenReturn(new VoiceSessionResponse(321L, "voice-100", NOW));

        CallSessionService service = service();
        CallSession result = service.prepare(100L);

        assertThat(result.getCallStatus()).isEqualTo(CallStatus.PREPARING);
        assertThat(result.getId()).isEqualTo(321L);
        assertThat(result.getVoiceSessionId()).isEqualTo("voice-100");

        ArgumentCaptor<CreateVoiceSessionRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateVoiceSessionRequest.class);
        verify(voiceServerClient).createSession(requestCaptor.capture());
        CreateVoiceSessionRequest request = requestCaptor.getValue();
        assertThat(request.callSessionId()).isEqualTo(321L);
        assertThat(request.userId()).isEqualTo(7L);
        assertThat(request.policySnapshot()).isEqualTo(policy);
        assertThat(request.expiresAt()).isEqualTo(Instant.parse("2026-09-08T12:07:00Z"));

        InOrder inOrder = inOrder(callSessionRepository, voiceServerClient);
        inOrder.verify(callSessionRepository).save(any(CallSession.class));
        inOrder.verify(voiceServerClient).createSession(any(CreateVoiceSessionRequest.class));
    }

    @Test
    void appliesFinalResultOnlyOnceAndDoesNotMoveStatusBackwards() {
        CallSession session = new CallSession(100L, reservation());
        session.markVoiceSession("voice-100");
        session.advanceTo(CallStatus.RINGING);
        session.advanceTo(CallStatus.CONNECTING);
        when(callSessionRepository.findById(100L)).thenReturn(Optional.of(session));

        CallSessionService service = service();
        service.applyResult(new CallResult(
                100L, "voice-100", CallStatus.IN_CALL, CallOutcome.SUCCEEDED,
                NOW, NOW.plusSeconds(10)));
        service.applyResult(new CallResult(
                100L, "voice-100", CallStatus.RINGING, CallOutcome.FAILED,
                NOW, NOW.plusSeconds(20)));

        assertThat(session.getCallStatus()).isEqualTo(CallStatus.IN_CALL);
        assertThat(session.getCallOutcome()).isEqualTo(CallOutcome.SUCCEEDED);
    }

    @Test
    void rejectsRingingSessionAndRequestsVoiceTermination() {
        CallSession session = new CallSession(100L, reservation());
        session.markVoiceSession("voice-100");
        session.advanceTo(CallStatus.RINGING);
        when(callSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(callSessionRepository.save(any(CallSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CallSessionService service = service();
        CallSessionResponse response = service.reject(7L, 100L);

        assertThat(session.getCallStatus()).isEqualTo(CallStatus.RINGING);
        assertThat(session.getCallOutcome()).isEqualTo(CallOutcome.FAILED);
        assertThat(response.callSessionId()).isEqualTo(100L);
        assertThat(response.callOutcome()).isEqualTo(CallOutcome.FAILED);
        verify(voiceServerClient).terminateSession("voice-100");

        InOrder inOrder = inOrder(voiceServerClient, callSessionRepository);
        inOrder.verify(voiceServerClient).terminateSession("voice-100");
        inOrder.verify(callSessionRepository).save(session);
    }

    @Test
    void rejectsCallSessionOwnedByAnotherUser() {
        CallSession session = new CallSession(100L, reservation());
        session.markVoiceSession("voice-100");
        when(callSessionRepository.findById(100L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service().reject(8L, 100L))
                .isInstanceOf(ReservationException.class)
                .hasMessage("Call session does not belong to user");

        verify(callSessionRepository, never()).save(any(CallSession.class));
        verify(voiceServerClient, never()).terminateSession(any());
    }

    @Test
    void rejectsOnlyRingingCallSessions() {
        CallSession session = new CallSession(100L, reservation());
        session.advanceTo(CallStatus.CONNECTING);
        when(callSessionRepository.findById(100L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service().reject(7L, 100L))
                .isInstanceOf(ReservationException.class)
                .hasMessage("Call session is not ringing");

        verify(callSessionRepository, never()).save(any(CallSession.class));
        verify(voiceServerClient, never()).terminateSession(any());
    }

    @Test
    void returnsEndedCallSessionWithoutTerminatingOrSavingAgain() {
        CallSession session = new CallSession(100L, reservation());
        session.markVoiceSession("voice-100");
        session.advanceTo(CallStatus.IN_CALL);
        session.complete(CallOutcome.SUCCEEDED, NOW, NOW.plusSeconds(10));
        when(callSessionRepository.findById(100L)).thenReturn(Optional.of(session));

        CallSessionResponse response = service().reject(7L, 100L);

        assertThat(response.callSessionId()).isEqualTo(100L);
        assertThat(response.callOutcome()).isEqualTo(CallOutcome.SUCCEEDED);
        verify(callSessionRepository, never()).save(any(CallSession.class));
        verify(voiceServerClient, never()).terminateSession(any());
    }

    @Test
    void doesNotMoveCallStatusBackwards() {
        CallSession session = new CallSession(100L, reservation());
        session.advanceTo(CallStatus.CONNECTING);

        session.advanceTo(CallStatus.RINGING);

        assertThat(session.getCallStatus()).isEqualTo(CallStatus.CONNECTING);
    }

    @Test
    void doesNotChangeCallSessionAfterItHasEnded() {
        CallSession session = new CallSession(100L, reservation());
        session.advanceTo(CallStatus.IN_CALL);
        session.complete(CallOutcome.SUCCEEDED, NOW, NOW.plusSeconds(10));

        session.advanceTo(CallStatus.RINGING);
        session.complete(CallOutcome.FAILED, NOW, NOW.plusSeconds(20));

        assertThat(session.getCallStatus()).isEqualTo(CallStatus.IN_CALL);
        assertThat(session.getCallOutcome()).isEqualTo(CallOutcome.SUCCEEDED);
        assertThat(session.getEndedAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void preparesAtMostOneCallSessionForAReservation() {
        CallSession existing = new CallSession(100L, reservation());
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation()));
        when(callSessionRepository.findByReservationId(100L)).thenReturn(Optional.of(existing));

        CallSession result = service().prepare(100L);

        assertThat(result).isSameAs(existing);
        verify(callSessionRepository).findByReservationId(100L);
        verify(voiceServerClient, never()).createSession(any(CreateVoiceSessionRequest.class));
    }

    @Test
    void preparesOwnedReservationForAnImmediateTestCall() {
        Reservation reservation = reservation();
        DialoguePolicy policy = policy();
        when(reservationRepository.findByIdAndUser_Id(100L, 7L)).thenReturn(Optional.of(reservation));
        when(callSessionRepository.findByReservationId(100L)).thenReturn(Optional.empty());
        when(callSessionRepository.save(any(CallSession.class)))
                .thenAnswer(invocation -> {
                    CallSession callSession = invocation.getArgument(0);
                    return callSession.getId() == null
                            ? new CallSession(321L, callSession.getReservation())
                            : callSession;
                });
        when(dialoguePolicyService.build(
                reservation.getPersona(), reservation.getScenario(),
                reservation.getScenarioContext(), reservation.getCallGoal(),
                java.util.Locale.KOREAN)).thenReturn(policy);
        when(voiceServerClient.createSession(any(CreateVoiceSessionRequest.class)))
                .thenReturn(new VoiceSessionResponse(321L, "voice-100", NOW));

        CallSessionResponse result = service().prepareForTest(7L, 100L);

        assertThat(result.callSessionId()).isEqualTo(321L);
        assertThat(result.callStatus()).isEqualTo(CallStatus.PREPARING);
        ArgumentCaptor<CreateVoiceSessionRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateVoiceSessionRequest.class);
        verify(voiceServerClient).createSession(requestCaptor.capture());
        assertThat(requestCaptor.getValue().expiresAt()).isEqualTo(NOW.plusSeconds(7 * 60L));
    }

    @Test
    void ringsOwnedReservationForAnImmediateIncomingCall() {
        Reservation reservation = reservation();
        DialoguePolicy policy = policy();
        when(reservationRepository.findByIdAndUser_Id(100L, 7L)).thenReturn(Optional.of(reservation));
        when(callSessionRepository.findByReservationId(100L)).thenReturn(Optional.empty());
        when(callSessionRepository.save(any(CallSession.class)))
                .thenAnswer(invocation -> {
                    CallSession callSession = invocation.getArgument(0);
                    return callSession.getId() == null
                            ? new CallSession(321L, callSession.getReservation())
                            : callSession;
                });
        when(dialoguePolicyService.build(
                reservation.getPersona(), reservation.getScenario(),
                reservation.getScenarioContext(), reservation.getCallGoal(),
                java.util.Locale.KOREAN)).thenReturn(policy);
        when(voiceServerClient.createSession(any(CreateVoiceSessionRequest.class)))
                .thenReturn(new VoiceSessionResponse(321L, "voice-100", NOW));
        when(pushNotificationService.sendIncomingCall(
                7L, new IncomingCallPushPayload(321L, "Santa"))).thenReturn(true);

        CallSessionResponse result = service().ringForTest(7L, 100L);

        assertThat(result.callSessionId()).isEqualTo(321L);
        assertThat(result.callStatus()).isEqualTo(CallStatus.RINGING);
        verify(pushNotificationService).sendIncomingCall(
                7L, new IncomingCallPushPayload(321L, "Santa"));
    }

    @Test
    void replacesRingingVoiceSessionForAnotherImmediateTestCall() {
        Reservation reservation = reservation();
        DialoguePolicy policy = policy();
        CallSession existing = new CallSession(321L, reservation);
        existing.markVoiceSession("voice-old");
        existing.advanceTo(CallStatus.RINGING);
        when(reservationRepository.findByIdAndUser_Id(100L, 7L)).thenReturn(Optional.of(reservation));
        when(callSessionRepository.findByReservationId(100L)).thenReturn(Optional.of(existing));
        when(callSessionRepository.save(any(CallSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dialoguePolicyService.build(
                reservation.getPersona(), reservation.getScenario(),
                reservation.getScenarioContext(), reservation.getCallGoal(),
                java.util.Locale.KOREAN)).thenReturn(policy);
        when(voiceServerClient.createSession(any(CreateVoiceSessionRequest.class)))
                .thenReturn(new VoiceSessionResponse(321L, "voice-new", NOW));
        when(pushNotificationService.sendIncomingCall(
                7L, new IncomingCallPushPayload(321L, "Santa"))).thenReturn(true);

        CallSessionResponse result = service().ringForTest(7L, 100L);

        assertThat(result.callStatus()).isEqualTo(CallStatus.RINGING);
        assertThat(existing.getVoiceSessionId()).isEqualTo("voice-new");
        verify(voiceServerClient).terminateSession("voice-old");
        verify(voiceServerClient).createSession(any(CreateVoiceSessionRequest.class));
        verify(pushNotificationService).sendIncomingCall(
                7L, new IncomingCallPushPayload(321L, "Santa"));
    }

    @Test
    void reusesEndedCallSessionForAnotherImmediateTestCall() {
        Reservation reservation = reservation();
        DialoguePolicy policy = policy();
        CallSession existing = new CallSession(321L, reservation);
        existing.markVoiceSession("voice-old");
        existing.advanceTo(CallStatus.IN_CALL);
        existing.complete(CallOutcome.FAILED, NOW, NOW.plusSeconds(10));
        when(reservationRepository.findByIdAndUser_Id(100L, 7L)).thenReturn(Optional.of(reservation));
        when(callSessionRepository.findByReservationId(100L)).thenReturn(Optional.of(existing));
        when(callSessionRepository.save(any(CallSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dialoguePolicyService.build(
                reservation.getPersona(), reservation.getScenario(),
                reservation.getScenarioContext(), reservation.getCallGoal(),
                java.util.Locale.KOREAN)).thenReturn(policy);
        when(voiceServerClient.createSession(any(CreateVoiceSessionRequest.class)))
                .thenReturn(new VoiceSessionResponse(321L, "voice-new", NOW));

        CallSessionResponse result = service().prepareForTest(7L, 100L);

        assertThat(result.callSessionId()).isEqualTo(321L);
        assertThat(result.callStatus()).isEqualTo(CallStatus.PREPARING);
        assertThat(result.callOutcome()).isNull();
        assertThat(existing.getEndedAt()).isNull();
        assertThat(existing.getVoiceSessionId()).isEqualTo("voice-new");
        InOrder inOrder = inOrder(voiceServerClient);
        inOrder.verify(voiceServerClient).terminateSession("voice-old");
        inOrder.verify(voiceServerClient).createSession(any(CreateVoiceSessionRequest.class));
    }

    @Test
    void ringsDueCallAndMarksItFailedWhenPushDeliveryFails() {
        CallSession session = new CallSession(100L, reservation());
        session.markVoiceSession("voice-100");
        when(callSessionRepository.findDueForRinging(NOW)).thenReturn(List.of(session));
        when(pushNotificationService.sendIncomingCall(
                7L, new IncomingCallPushPayload(100L, "Santa"))).thenReturn(false);
        when(callSessionRepository.save(any(CallSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().ringDueCallSessions(NOW);

        assertThat(session.getCallStatus()).isEqualTo(CallStatus.RINGING);
        assertThat(session.getCallOutcome()).isEqualTo(CallOutcome.FAILED);
        assertThat(session.getEndedAt()).isEqualTo(NOW);
        verify(callSessionRepository, org.mockito.Mockito.times(2)).save(session);
    }

    @Test
    void ringsDueCallWhenPushDeliverySucceeds() {
        CallSession session = new CallSession(100L, reservation());
        session.markVoiceSession("voice-100");
        when(callSessionRepository.findDueForRinging(NOW)).thenReturn(List.of(session));
        when(pushNotificationService.sendIncomingCall(
                7L, new IncomingCallPushPayload(100L, "Santa"))).thenReturn(true);
        when(callSessionRepository.save(any(CallSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().ringDueCallSessions(NOW);

        assertThat(session.getCallStatus()).isEqualTo(CallStatus.RINGING);
        assertThat(session.getCallOutcome()).isNull();
        verify(callSessionRepository).save(session);
    }

    private CallSessionService service() {
        return new CallSessionService(
                reservationRepository,
                callSessionRepository,
                dialoguePolicyService,
                voiceServerClient,
                pushNotificationService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Reservation reservation() {
        return new Reservation(
                new User(7L), persona(), scenario(), "context", "goal",
                Instant.parse("2026-09-08T12:00:00Z"), "Asia/Seoul");
    }

    private static Persona persona() {
        return new Persona(11L, "santa", "Santa", "description", "context",
                "instructions", List.of("persona rule"), "santa-default", "/santa.png",
                "/santa.mp3", "ACTIVE");
    }

    private static Scenario scenario() {
        return new Scenario(21L, "child-roleplay", "Child roleplay", "description",
                "context", "goal", "scenario instructions", List.of("scenario rule"), "ACTIVE");
    }

    private static DialoguePolicy policy() {
        return new DialoguePolicy("Santa", List.of("greeting"), "goal", List.of(),
                List.of("forbidden"), List.of("end"), "ko-KR", "santa-default",
                List.of("instructions"), List.of("rules"), "context", Map.of());
    }
}
