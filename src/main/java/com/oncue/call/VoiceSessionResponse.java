package com.oncue.call;

import java.time.Instant;

public record VoiceSessionResponse(
        Long callSessionId,
        String voiceSessionId,
        Instant createdAt
) {
}
