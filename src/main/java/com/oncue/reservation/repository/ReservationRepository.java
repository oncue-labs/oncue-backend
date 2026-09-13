package com.oncue.reservation.repository;

import com.oncue.reservation.model.Reservation;
import com.oncue.reservation.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByUser_IdAndReservationStatus(Long userId, ReservationStatus reservationStatus);

    List<Reservation> findByUser_IdOrderByScheduledAtUtcAsc(Long userId);

    Optional<Reservation> findByIdAndUser_Id(Long reservationId, Long userId);
}
