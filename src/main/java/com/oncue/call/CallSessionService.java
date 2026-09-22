package com.oncue.call;

import com.oncue.conversation.model.DialoguePolicy;
import com.oncue.conversation.service.DialoguePolicyService;
import com.oncue.reservation.model.Reservation;
import com.oncue.reservation.exception.ReservationException;
import com.oncue.reservation.repository.ReservationRepository;
import com.oncue.push.IncomingCallPushPayload;
import com.oncue.push.PushNotificationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
public class CallSessionService {

    private final ReservationRepository reservationRepository;
    private final CallSessionRepository callSessionRepository;
    private final DialoguePolicyService dialoguePolicyService;
    private final VoiceServerClient voiceServerClient;
    private final PushNotificationService pushNotificationService;
    private final Clock clock;

    @Autowired
    public CallSessionService(
            ReservationRepository reservationRepository,
            CallSessionRepository callSessionRepository,
            DialoguePolicyService dialoguePolicyService,
            ObjectProvider<VoiceServerClient> voiceServerClientProvider,
            ObjectProvider<PushNotificationService> pushNotificationServiceProvider) {
        this(reservationRepository, callSessionRepository, dialoguePolicyService,
                voiceServerClientProvider.getIfAvailable(),
                pushNotificationServiceProvider.getIfAvailable(), Clock.systemUTC());
    }

    public CallSessionService(
            ReservationRepository reservationRepository,
            CallSessionRepository callSessionRepository,
            DialoguePolicyService dialoguePolicyService,
            VoiceServerClient voiceServerClient,
            Clock clock) {
        this(reservationRepository, callSessionRepository, dialoguePolicyService,
                voiceServerClient, null, clock);
    }

    public CallSessionService(
            ReservationRepository reservationRepository,
            CallSessionRepository callSessionRepository,
            DialoguePolicyService dialoguePolicyService,
            VoiceServerClient voiceServerClient,
            PushNotificationService pushNotificationService,
            Clock clock) {
        this.reservationRepository = reservationRepository;
        this.callSessionRepository = callSessionRepository;
        this.dialoguePolicyService = dialoguePolicyService;
        this.voiceServerClient = voiceServerClient;
        this.pushNotificationService = pushNotificationService;
        this.clock = clock;
    }

    @Transactional
    public CallSession prepare(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
        return callSessionRepository.findByReservationId(reservationId)
                .orElseGet(() -> createCallSession(
                        reservation, reservation.getScheduledAtUtc().plus(7, ChronoUnit.MINUTES)));
    }

    @Transactional
    public CallSessionResponse prepareForTest(Long userId, Long reservationId) {
        return toResponse(prepareForTestCallSession(userId, reservationId));
    }

    @Transactional
    public CallSessionResponse ringForTest(Long userId, Long reservationId) {
        CallSession callSession = prepareForTestCallSession(userId, reservationId);
        ringCallSession(callSession, clock.instant());
        return toResponse(callSession);
    }

    private CallSession prepareForTestCallSession(Long userId, Long reservationId) {
        Reservation reservation = reservationRepository.findByIdAndUser_Id(reservationId, userId)
                .orElseThrow(() -> new ReservationException(HttpStatus.NOT_FOUND, "Reservation not found"));
        java.util.Optional<CallSession> existing = callSessionRepository.findByReservationId(reservationId);
        if (existing.isPresent()) {
            CallSession callSession = existing.get();
            if (callSession.isEnded()) {
                if (callSession.getVoiceSessionId() != null) {
                    requireVoiceServerClient().terminateSession(callSession.getVoiceSessionId());
                }
                callSession.resetForTest();
                return attachVoiceSession(
                        callSession, reservation, clock.instant().plus(7, ChronoUnit.MINUTES));
            }
            return callSession;
        }

        return createCallSession(reservation, clock.instant().plus(7, ChronoUnit.MINUTES));
    }

    @Transactional
    public void applyResult(CallResult result) {
        callSessionRepository.findById(result.callSessionId()).ifPresent(callSession -> {
            if (hasDifferentVoiceSession(callSession, result.voiceSessionId())) {
                return;
            }
            callSession.advanceTo(result.callStatus());
            if (result.callOutcome() != null) {
                Instant callStartedAt = result.startedAt() == null
                        ? callSession.getStartedAt()
                        : result.startedAt();
                Instant callEndedAt = result.endedAt() == null
                        ? clock.instant()
                        : result.endedAt();
                callSession.complete(result.callOutcome(), callStartedAt, callEndedAt);
            }
            callSessionRepository.save(callSession);
        });
    }

    @Transactional
    public void reconcileExpiredCallSessions(Instant now) {
        Instant scheduledBefore = now.minus(7, ChronoUnit.MINUTES);
        List<CallSession> unfinishedSessions = callSessionRepository.findUnfinishedBefore(scheduledBefore);
        unfinishedSessions.forEach(callSession -> {
            CallOutcome outcome = callSession.getCallStatus() == CallStatus.IN_CALL
                    ? CallOutcome.SUCCEEDED
                    : CallOutcome.FAILED;
            callSession.complete(outcome, callSession.getStartedAt(), now);
            callSessionRepository.save(callSession);
        });
    }

    @Transactional
    public void ringDueCallSessions(Instant now) {
        callSessionRepository.findDueForRinging(now)
                .forEach(callSession -> ringCallSession(callSession, now));
    }

    @Transactional
    public CallSessionResponse reject(Long userId, Long callSessionId) {
        CallSession callSession = callSessionRepository.findById(callSessionId)
                .orElseThrow(() -> new ReservationException(HttpStatus.NOT_FOUND, "Call session not found"));
        if (!userId.equals(callSession.getReservation().getUser().getId())) {
            throw new ReservationException(HttpStatus.FORBIDDEN, "Call session does not belong to user");
        }
        if (callSession.isEnded()) {
            return toResponse(callSession);
        }
        if (callSession.getCallStatus() != CallStatus.RINGING) {
            throw new ReservationException(HttpStatus.CONFLICT, "Call session is not ringing");
        }

        if (callSession.getVoiceSessionId() != null) {
            requireVoiceServerClient().terminateSession(callSession.getVoiceSessionId());
        }
        Instant now = clock.instant();
        Instant callStartedAt = callSession.getStartedAt() == null ? now : callSession.getStartedAt();
        callSession.complete(CallOutcome.FAILED, callStartedAt, now);
        CallSession savedCallSession = callSessionRepository.save(callSession);
        return toResponse(savedCallSession);
    }

    private CallSessionResponse toResponse(CallSession callSession) {
        return new CallSessionResponse(
                callSession.getId(),
                callSession.getCallStatus(),
                callSession.getCallOutcome(),
                callSession.getCreatedAt(),
                callSession.getEndedAt());
    }

    private void ringCallSession(CallSession callSession, Instant now) {
        if (callSession.getCallStatus() != CallStatus.PREPARING) {
            return;
        }
        callSession.advanceTo(CallStatus.RINGING);
        callSessionRepository.save(callSession);

        boolean delivered = pushNotificationService != null
                && pushNotificationService.sendIncomingCall(
                        callSession.getReservation().getUser().getId(),
                        new IncomingCallPushPayload(
                                callSession.getId(),
                                callSession.getReservation().getPersona().getName()));
        if (!delivered) {
            callSession.complete(CallOutcome.FAILED, callSession.getStartedAt(), now);
            callSessionRepository.save(callSession);
        }
    }

    private CallSession createCallSession(Reservation reservation, Instant expiresAt) {
        CallSession callSession = callSessionRepository.save(new CallSession(reservation));
        return attachVoiceSession(callSession, reservation, expiresAt);
    }

    private CallSession attachVoiceSession(
            CallSession callSession,
            Reservation reservation,
            Instant expiresAt) {
        DialoguePolicy dialoguePolicy = dialoguePolicyService.build(
                reservation.getPersona(),
                reservation.getScenario(),
                reservation.getScenarioContext(),
                reservation.getCallGoal(),
                Locale.KOREAN);
        VoiceSessionResponse voiceSession = requireVoiceServerClient().createSession(
                new CreateVoiceSessionRequest(
                        callSession.getId(),
                        reservation.getUser().getId(),
                        dialoguePolicy,
                        expiresAt));
        callSession.markVoiceSession(voiceSession.voiceSessionId());
        return callSessionRepository.save(callSession);
    }

    private boolean hasDifferentVoiceSession(CallSession callSession, String voiceSessionId) {
        return callSession.getVoiceSessionId() != null
                && voiceSessionId != null
                && !callSession.getVoiceSessionId().equals(voiceSessionId);
    }

    private VoiceServerClient requireVoiceServerClient() {
        if (voiceServerClient == null) {
            throw new IllegalStateException("Voice server client is not configured");
        }
        return voiceServerClient;
    }
}
