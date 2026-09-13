package com.oncue.reservation.repository;

import com.oncue.call.CallSession;
import com.oncue.reservation.model.Reservation;
import com.oncue.reservation.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByUser_IdAndReservationStatus(Long userId, ReservationStatus reservationStatus);

    List<Reservation> findByUser_IdOrderByScheduledAtUtcAsc(Long userId);

    Optional<Reservation> findByIdAndUser_Id(Long reservationId, Long userId);

    @Query("""
            select reservation
            from Reservation reservation
            where reservation.reservationStatus = :status
              and reservation.scheduledAtUtc > :now
              and reservation.scheduledAtUtc <= :dueUntil
              and not exists (
                  select callSession.id
                  from CallSession callSession
                  where callSession.reservation.id = reservation.id
              )
            order by reservation.scheduledAtUtc asc
            """)
    List<Reservation> findDueForCallPreparation(
            @Param("status") ReservationStatus status,
            @Param("now") Instant now,
            @Param("dueUntil") Instant dueUntil);
}
