package com.oncue.auth.controller.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String provider,
        String providerAccessToken,
        String authorizationCode,
        String codeVerifier) {
}
