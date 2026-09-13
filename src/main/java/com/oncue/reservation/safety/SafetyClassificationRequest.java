package com.oncue.reservation.safety;

public record SafetyClassificationRequest(
        String personaKey,
        String scenarioKey,
        String scenarioContext,
        String callGoal
) {
}
