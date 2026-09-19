package com.oncue.push;

import java.time.Instant;

public record PushDeviceResponse(
        String deviceToken,
        PushPlatform platform,
        PushEnvironment environment,
        Instant createdAt,
        Instant updatedAt) {
}
