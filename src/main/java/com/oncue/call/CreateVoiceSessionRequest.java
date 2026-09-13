package com.oncue.call;

import com.oncue.conversation.model.DialoguePolicy;

import java.time.Instant;

public record CreateVoiceSessionRequest(
        Long callSessionId,
        Long userId,
        DialoguePolicy policySnapshot,
        Instant expiresAt
) {
}
