package com.oncue.reservation.scheduler;

import com.oncue.call.CallSessionService;
import com.oncue.reservation.model.Reservation;
import com.oncue.reservation.model.ReservationStatus;
import com.oncue.reservation.repository.ReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Creates preparation data for reservations that are due before their scheduled time. */
@Component
public class ReservationPreparationScheduler {

    private static final long PREPARATION_WINDOW_MINUTES = 3L;

    private final ReservationRepository reservationRepository;
    private final CallSessionService callSessionService;
    private final Clock clock;

    @Autowired
    public ReservationPreparationScheduler(
            ReservationRepository reservationRepository,
            CallSessionService callSessionService) {
        this(reservationRepository, callSessionService, Clock.systemUTC());
    }

    ReservationPreparationScheduler(
            ReservationRepository reservationRepository,
            CallSessionService callSessionService,
            Clock clock) {
        this.reservationRepository = reservationRepository;
        this.callSessionService = callSessionService;
        this.clock = clock;
    }

    @Scheduled(fixedRateString = "${oncue.reservation.preparation-interval-ms:60000}")
    public void prepareDueReservations() {
        prepareDueReservations(Instant.now(clock));
    }

    void prepareDueReservations(Instant now) {
        Instant dueUntil = now.plus(PREPARATION_WINDOW_MINUTES, ChronoUnit.MINUTES);
        reservationRepository.findDueForCallPreparation(
                        ReservationStatus.SCHEDULED, now, dueUntil)
                .forEach(this::prepare);
    }

    private void prepare(Reservation reservation) {
        callSessionService.prepare(reservation.getId());
    }
}
