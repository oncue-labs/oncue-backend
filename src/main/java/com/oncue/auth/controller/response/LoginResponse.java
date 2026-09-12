package com.oncue.auth.controller.response;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        Instant expiresAt,
        Instant createdAt) {
}
