package com.oncue.common.security;

import java.time.Instant;

public record AccessToken(String value, Instant expiresAt) {
}
