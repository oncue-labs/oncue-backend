package com.oncue.call;

import java.time.Instant;

public record CallSessionResponse(
        Long callSessionId,
        CallStatus callStatus,
        CallOutcome callOutcome,
        Instant createdAt,
        Instant endedAt) {
}
