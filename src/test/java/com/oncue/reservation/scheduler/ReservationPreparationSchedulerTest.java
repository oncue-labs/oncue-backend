package com.oncue.reservation.scheduler;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oncue.call.CallSessionService;
import com.oncue.reservation.model.Reservation;
import com.oncue.reservation.model.ReservationStatus;
import com.oncue.reservation.repository.ReservationRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationPreparationSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-08T11:57:00Z");

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private CallSessionService callSessionService;

    @Test
    void preparesScheduledReservationsWithinNextThreeMinutesWithoutCallSessions() {
        Reservation dueReservation = org.mockito.Mockito.mock(Reservation.class);
        when(reservationRepository.findDueForCallPreparation(
                eq(ReservationStatus.SCHEDULED), eq(NOW), eq(NOW.plusSeconds(180))))
                .thenReturn(List.of(dueReservation));

        ReservationPreparationScheduler scheduler =
                new ReservationPreparationScheduler(reservationRepository, callSessionService);

        scheduler.prepareDueReservations(NOW);

        verify(reservationRepository).findDueForCallPreparation(
                ReservationStatus.SCHEDULED, NOW, NOW.plusSeconds(180));
        verify(callSessionService).prepare(dueReservation.getId());
    }
}
