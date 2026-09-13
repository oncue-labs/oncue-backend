package com.oncue.reservation.safety;

public interface SafetyClassifierClient {

    SafetyDecision classify(SafetyClassificationRequest request);
}
