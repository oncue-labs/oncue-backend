package com.oncue.call;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CallSessionRepository extends JpaRepository<CallSession, Long> {

    @Query("select callSession from CallSession callSession where callSession.reservation.id = :reservationId")
    Optional<CallSession> findByReservationId(@Param("reservationId") Long reservationId);
}
