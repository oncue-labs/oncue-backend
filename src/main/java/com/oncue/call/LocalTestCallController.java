package com.oncue.call;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Development-only endpoints that bypass reservation timing. */
@RestController
@RequestMapping("/api/v1/reservations")
public class LocalTestCallController {

    private final CallSessionService callSessionService;
    private final boolean enabled;

    public LocalTestCallController(
            CallSessionService callSessionService,
            @Value("${oncue.features.local-test-call-enabled:false}") boolean enabled) {
        this.callSessionService = callSessionService;
        this.enabled = enabled;
    }

    @PostMapping("/{reservationId}/test-call")
    public ResponseEntity<CallSessionResponse> prepare(
            Authentication authentication,
            @PathVariable Long reservationId) {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(callSessionService.prepareForTest(userId, reservationId));
    }

    @PostMapping("/{reservationId}/test-incoming-call")
    public ResponseEntity<CallSessionResponse> ring(
            Authentication authentication,
            @PathVariable Long reservationId) {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(callSessionService.ringForTest(userId, reservationId));
    }
}
