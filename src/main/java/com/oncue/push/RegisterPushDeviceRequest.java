package com.oncue.push;

import jakarta.validation.constraints.NotBlank;

public record RegisterPushDeviceRequest(
        /** Hex-encoded APNs VoIP device token issued for this app installation. */
        @NotBlank String deviceToken,
        /** Mobile platform that owns the token. MVP accepts IOS only. */
        @NotBlank String platform,
        /** APNs endpoint environment: SANDBOX for development builds or PRODUCTION for TestFlight. */
        @NotBlank String environment) {
}
