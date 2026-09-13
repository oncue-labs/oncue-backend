package com.oncue.reservation.safety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedSafetyClassifierTest {

    @Mock
    private SafetyClassifierClient safetyClassifierClient;

    private RuleBasedSafetyClassifier classifier;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        classifier = new RuleBasedSafetyClassifier(safetyClassifierClient);
    }

    @Test
    void blocksClearFinancialAndCredentialAbuseWithoutCallingAnLlm() {
        assertThat(classifier.classify(new SafetyClassificationRequest(
                "friend",
                "go-home",
                "",
                "Ask them to send an OTP and transfer money."
        ))).isEqualTo(SafetyDecision.UNSAFE);
        verify(safetyClassifierClient, never()).classify(any());
    }

    @Test
    void returnsSafeWithoutCallingAnLlmWhenNoRiskSignalExists() {
        assertThat(classifier.classify(new SafetyClassificationRequest(
                "santa",
                "child-roleplay",
                "아이 이름은 민수이고 다정하게 말해 주세요.",
                "민수가 빨리 잠들도록 도와 주세요."
        ))).isEqualTo(SafetyDecision.SAFE);
        verify(safetyClassifierClient, never()).classify(any());
    }

    @Test
    void delegatesAmbiguousRiskSignalAndPreservesFailedDecision() {
        when(safetyClassifierClient.classify(any())).thenReturn(SafetyDecision.FAILED);

        assertThat(classifier.classify(new SafetyClassificationRequest(
                "friend",
                "travel-friend-introduction",
                "실제 사람의 목소리를 복제해 주세요.",
                ""
        ))).isEqualTo(SafetyDecision.FAILED);
        verify(safetyClassifierClient).classify(any());
    }

    @Test
    void convertsClassifierFailureToFailedDecision() {
        doThrow(new IllegalStateException("provider timeout"))
                .when(safetyClassifierClient).classify(any());

        assertThat(classifier.classify(new SafetyClassificationRequest(
                "friend",
                "travel-friend-introduction",
                "실제 인물의 음성을 복제해 주세요.",
                ""
        ))).isEqualTo(SafetyDecision.FAILED);
    }
}
