package com.oncue.call;

import com.oncue.reservation.exception.ReservationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/call-sessions")
public class CallSessionResultController {

    private final CallSessionService callSessionService;
    private final byte[] serviceToken;

    public CallSessionResultController(
            CallSessionService callSessionService,
            @Value("${oncue.voice.service-token:}") String serviceToken) {
        this.callSessionService = callSessionService;
        this.serviceToken = serviceToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/{callSessionId}/result")
    public ResponseEntity<Void> receiveResult(
            @PathVariable Long callSessionId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody CallSessionResultRequest request) {
        requireServiceToken(authorization);
        callSessionService.applyResult(new CallResult(
                callSessionId,
                request.voiceSessionId(),
                request.callStatus(),
                request.callOutcome(),
                request.startedAt(),
                request.endedAt()));
        return ResponseEntity.noContent().build();
    }

    private void requireServiceToken(String authorization) {
        String prefix = "Bearer ";
        if (authorization == null || !authorization.startsWith(prefix)) {
            throw unauthorized();
        }
        byte[] receivedToken = authorization.substring(prefix.length())
                .getBytes(StandardCharsets.UTF_8);
        if (serviceToken.length == 0
                || !MessageDigest.isEqual(serviceToken, receivedToken)) {
            throw unauthorized();
        }
    }

    private static ReservationException unauthorized() {
        return new ReservationException(HttpStatus.UNAUTHORIZED, "service authentication required");
    }
}
