package com.oncue.auth.controller.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String provider,
        @NotBlank String authorizationCode,
        @NotBlank String codeVerifier) {
}
