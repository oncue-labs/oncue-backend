package com.oncue.reservation.service;

import com.oncue.auth.model.User;
import com.oncue.auth.repository.UserRepository;
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
import com.oncue.reservation.safety.RuleBasedSafetyClassifier;
import com.oncue.reservation.safety.SafetyClassificationRequest;
import com.oncue.reservation.safety.SafetyDecision;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    private static final long CALL_DURATION_SECONDS = 5 * 60L;

    private final UserRepository userRepository;
    private final PersonaRepository personaRepository;
    private final ScenarioRepository scenarioRepository;
    private final ReservationRepository reservationRepository;
    private final RuleBasedSafetyClassifier safetyClassifier;
    private final Clock clock;

    @Autowired
    public ReservationService(
            UserRepository userRepository,
            PersonaRepository personaRepository,
            ScenarioRepository scenarioRepository,
            ReservationRepository reservationRepository,
            RuleBasedSafetyClassifier safetyClassifier) {
        this(userRepository, personaRepository, scenarioRepository, reservationRepository,
                safetyClassifier, Clock.systemUTC());
    }

    public ReservationService(
            UserRepository userRepository,
            PersonaRepository personaRepository,
            ScenarioRepository scenarioRepository,
            ReservationRepository reservationRepository,
            RuleBasedSafetyClassifier safetyClassifier,
            Clock clock) {
        this.userRepository = userRepository;
        this.personaRepository = personaRepository;
        this.scenarioRepository = scenarioRepository;
        this.reservationRepository = reservationRepository;
        this.safetyClassifier = safetyClassifier;
        this.clock = clock;
    }

    @Transactional
    public ReservationResponse create(Long userId, CreateReservationRequest request) {
        User user = findUser(userId);
        Persona persona = findPersona(request.personaKey());
        Scenario scenario = findScenario(request.scenarioKey());
        Instant scheduledAtUtc = toUtc(request.scheduledAtLocal(), request.timeZone());
        validateSchedule(userId, scheduledAtUtc, null);
        validateSafety(request.personaKey(), request.scenarioKey(), request.scenarioContext(), request.callGoal());

        Reservation reservation = new Reservation(
                user,
                persona,
                scenario,
                request.scenarioContext(),
                request.callGoal(),
                scheduledAtUtc,
                request.timeZone());
        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> list(Long userId) {
        return reservationRepository.findByUser_IdOrderByScheduledAtUtcAsc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(Long userId, Long reservationId) {
        return toResponse(findReservation(userId, reservationId));
    }

    @Transactional
    public ReservationResponse update(Long userId, Long reservationId, UpdateReservationRequest request) {
        Reservation reservation = findReservation(userId, reservationId);
        validateEditable(reservation);

        String personaKey = valueOrDefault(request.personaKey(), reservation.getPersona().getKey());
        String scenarioKey = valueOrDefault(request.scenarioKey(), reservation.getScenario().getKey());
        String scenarioContext = valueOrDefault(request.scenarioContext(), reservation.getScenarioContext());
        String callGoal = valueOrDefault(request.callGoal(), reservation.getCallGoal());
        String timeZone = valueOrDefault(request.timeZone(), reservation.getTimeZone());
        Persona persona = findPersona(personaKey);
        Scenario scenario = findScenario(scenarioKey);
        if (request.scheduledAtLocal() == null) {
            validateTimeZone(timeZone);
        }
        Instant scheduledAtUtc = request.scheduledAtLocal() == null
                ? reservation.getScheduledAtUtc()
                : toUtc(request.scheduledAtLocal(), timeZone);

        validateSchedule(userId, scheduledAtUtc, reservationId);
        validateSafety(personaKey, scenarioKey, scenarioContext, callGoal);
        reservation.update(persona, scenario, scenarioContext, callGoal, scheduledAtUtc, timeZone);
        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional
    public ReservationResponse cancel(Long userId, Long reservationId) {
        Reservation reservation = findReservation(userId, reservationId);
        validateEditable(reservation);
        reservation.cancel(clock.instant());
        return toResponse(reservationRepository.save(reservation));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ReservationException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));
    }

    private Persona findPersona(String key) {
        return personaRepository.findActiveByKey(key)
                .orElseThrow(() -> new ReservationException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Active persona not found"));
    }

    private Scenario findScenario(String key) {
        return scenarioRepository.findActiveByKey(key)
                .orElseThrow(() -> new ReservationException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Active scenario not found"));
    }

    private Reservation findReservation(Long userId, Long reservationId) {
        return reservationRepository.findByIdAndUser_Id(reservationId, userId)
                .orElseThrow(() -> new ReservationException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Reservation not found"));
    }

    private void validateEditable(Reservation reservation) {
        if (reservation.getReservationStatus() != ReservationStatus.SCHEDULED) {
            throw new ReservationException(
                    org.springframework.http.HttpStatus.CONFLICT, "Reservation is not editable");
        }
        if (!clock.instant().isBefore(reservation.getEditableUntil())) {
            throw new ReservationException(
                    org.springframework.http.HttpStatus.CONFLICT, "Reservation is within the five-minute lock window");
        }
    }

    private void validateSchedule(Long userId, Instant scheduledAtUtc, Long excludedReservationId) {
        if (!scheduledAtUtc.isAfter(clock.instant())) {
            throw new ReservationException(
                    org.springframework.http.HttpStatus.CONFLICT, "Scheduled time must be in the future");
        }
        boolean overlaps = reservationRepository
                .findByUser_IdAndReservationStatus(userId, ReservationStatus.SCHEDULED)
                .stream()
                .filter(reservation -> excludedReservationId == null
                        || !excludedReservationId.equals(reservation.getId()))
                .anyMatch(reservation -> overlaps(reservation.getScheduledAtUtc(), scheduledAtUtc));
        if (overlaps) {
            throw new ReservationException(
                    org.springframework.http.HttpStatus.CONFLICT, "Reservation time overlaps another reservation");
        }
    }

    private boolean overlaps(Instant existingStart, Instant requestedStart) {
        Instant existingEnd = existingStart.plusSeconds(CALL_DURATION_SECONDS);
        Instant requestedEnd = requestedStart.plusSeconds(CALL_DURATION_SECONDS);
        return existingStart.isBefore(requestedEnd) && requestedStart.isBefore(existingEnd);
    }

    private void validateSafety(String personaKey, String scenarioKey, String scenarioContext, String callGoal) {
        SafetyDecision decision = safetyClassifier.classify(
                new SafetyClassificationRequest(personaKey, scenarioKey, scenarioContext, callGoal));
        if (decision != SafetyDecision.SAFE) {
            throw new ReservationException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "Reservation content cannot be accepted");
        }
    }

    private Instant toUtc(LocalDateTime scheduledAtLocal, String timeZone) {
        try {
            return scheduledAtLocal.atZone(ZoneId.of(timeZone)).toInstant();
        } catch (DateTimeException exception) {
            throw new ReservationException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid time zone");
        }
    }

    private void validateTimeZone(String timeZone) {
        try {
            ZoneId.of(timeZone);
        } catch (DateTimeException exception) {
            throw new ReservationException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid time zone");
        }
    }

    private ReservationResponse toResponse(Reservation reservation) {
        ZoneId zoneId = ZoneId.of(reservation.getTimeZone());
        LocalDateTime scheduledAtLocal = LocalDateTime.ofInstant(reservation.getScheduledAtUtc(), zoneId);
        return new ReservationResponse(
                reservation.getId(),
                reservation.getReservationStatus().name(),
                reservation.getPersona().getKey(),
                reservation.getScenario().getKey(),
                reservation.getScenarioContext(),
                reservation.getCallGoal(),
                scheduledAtLocal,
                reservation.getTimeZone(),
                reservation.getScheduledAtUtc(),
                reservation.getEditableUntil(),
                reservation.getCreatedAt(),
                null,
                null);
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }
}
