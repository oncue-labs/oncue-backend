package com.oncue.call;

import java.time.Instant;

public record CallSessionResultRequest(
        String voiceSessionId,
        CallStatus callStatus,
        CallOutcome callOutcome,
        Instant startedAt,
        Instant endedAt
) {
}
