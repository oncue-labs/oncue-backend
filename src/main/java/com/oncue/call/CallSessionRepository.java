package com.oncue.call;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CallSessionRepository extends JpaRepository<CallSession, Long> {

    @Query("select callSession from CallSession callSession where callSession.reservation.id = :reservationId")
    Optional<CallSession> findByReservationId(@Param("reservationId") Long reservationId);

    @Query("""
            select callSession
            from CallSession callSession
            where callSession.callOutcome is null
              and callSession.reservation.scheduledAtUtc <= :scheduledBefore
            order by callSession.reservation.scheduledAtUtc asc
            """)
    List<CallSession> findUnfinishedBefore(@Param("scheduledBefore") Instant scheduledBefore);
}
