package com.oncue.reservation.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateReservationRequest(
        @NotBlank String personaKey,
        @NotBlank String scenarioKey,
        @NotBlank String scenarioContext,
        @NotBlank String callGoal,
        @NotNull LocalDateTime scheduledAtLocal,
        @NotBlank String timeZone
) {
}
