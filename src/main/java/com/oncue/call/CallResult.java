package com.oncue.call;

import java.time.Instant;

public record CallResult(
        Long callSessionId,
        String voiceSessionId,
        CallStatus callStatus,
        CallOutcome callOutcome,
        Instant startedAt,
        Instant endedAt
) {
}
