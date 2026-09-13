package com.oncue.call;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/call-sessions")
public class ConnectionTokenController {

    private final ConnectionTokenService connectionTokenService;

    public ConnectionTokenController(ConnectionTokenService connectionTokenService) {
        this.connectionTokenService = connectionTokenService;
    }

    @PostMapping("/{callSessionId}/connection-token")
    public ResponseEntity<ConnectionTokenResponse> issue(
            Authentication authentication,
            @PathVariable Long callSessionId) {
        Long userId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(connectionTokenService.issue(userId, callSessionId));
    }
}
