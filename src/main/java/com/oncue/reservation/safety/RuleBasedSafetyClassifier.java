package com.oncue.reservation.safety;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** 예약 입력의 명확한 위험 신호를 먼저 확인하고, 애매한 신호만 외부 판정기에 전달하는 안전 판정기. */
@Service
public class RuleBasedSafetyClassifier {

    private static final Pattern CLEARLY_UNSAFE = Pattern.compile(
            "(password|passcode|otp|one[- ]time password|verification code|인증번호|비밀번호|" +
                    "send money|transfer money|bank account|credit card|investment|송금|계좌|카드|투자|" +
                    "sextortion|sexual grooming|blackmail|섹스토션|성적 그루밍|성적 협박|" +
                    "kidnap|ransom|suicide|self-harm|납치|몸값|자해)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern REQUIRES_CLASSIFICATION = Pattern.compile(
            "(romance scam|romance|voice clone|clone.*voice|impersonat|real person|" +
                    "stalk|surveillance|phishing|account takeover|로맨스|음성 복제|사칭|" +
                    "실제\\s*(사람|인물)|목소리\\s*.*복제|음성\\s*.*복제|" +
                    "스토킹|감시|피싱|계정 탈취)",
            Pattern.CASE_INSENSITIVE);

    private final SafetyClassifierClient safetyClassifierClient;

    public RuleBasedSafetyClassifier(SafetyClassifierClient safetyClassifierClient) {
        this.safetyClassifierClient = safetyClassifierClient;
    }

    public SafetyDecision classify(SafetyClassificationRequest request) {
        String input = String.join(" ",
                valueOrEmpty(request.scenarioContext()),
                valueOrEmpty(request.callGoal())).toLowerCase(Locale.ROOT);

        if (CLEARLY_UNSAFE.matcher(input).find()) {
            return SafetyDecision.UNSAFE;
        }
        if (REQUIRES_CLASSIFICATION.matcher(input).find()) {
            try {
                return safetyClassifierClient.classify(request);
            } catch (RuntimeException exception) {
                return SafetyDecision.FAILED;
            }
        }
        return SafetyDecision.SAFE;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
