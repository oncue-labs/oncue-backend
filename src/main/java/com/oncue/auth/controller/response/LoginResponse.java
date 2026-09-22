package com.oncue.auth.controller.response;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        Instant expiresAt,
        Instant createdAt,
        String refreshToken,
        Instant refreshTokenExpiresAt) {

    public LoginResponse(String accessToken, Instant expiresAt, Instant createdAt) {
        this(accessToken, expiresAt, createdAt, null, null);
    }
}
