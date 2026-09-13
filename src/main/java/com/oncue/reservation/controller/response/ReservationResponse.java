package com.oncue.reservation.controller.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReservationResponse(
        Long reservationId,
        String reservationStatus,
        String personaKey,
        String scenarioKey,
        String scenarioContext,
        String callGoal,
        LocalDateTime scheduledAtLocal,
        String timeZone,
        Instant scheduledAtUtc,
        Instant editableUntil,
        Instant createdAt,
        String callStatus,
        String callOutcome
) {
}
