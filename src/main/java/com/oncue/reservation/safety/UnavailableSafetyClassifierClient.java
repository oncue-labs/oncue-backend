package com.oncue.reservation.safety;

import org.springframework.stereotype.Component;

/** 외부 안전 판정 연동 전까지 애매한 요청을 차단하는 기본 구현. */
@Component
public class UnavailableSafetyClassifierClient implements SafetyClassifierClient {

    @Override
    public SafetyDecision classify(SafetyClassificationRequest request) {
        return SafetyDecision.FAILED;
    }
}
