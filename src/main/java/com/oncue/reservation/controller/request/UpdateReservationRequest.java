package com.oncue.reservation.controller.request;

import java.time.LocalDateTime;

public record UpdateReservationRequest(
        String personaKey,
        String scenarioKey,
        String scenarioContext,
        String callGoal,
        LocalDateTime scheduledAtLocal,
        String timeZone
) {
}
