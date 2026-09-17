package com.oncue.call;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/call-sessions")
public class CallSessionController {

    private final CallSessionService callSessionService;

    public CallSessionController(CallSessionService callSessionService) {
        this.callSessionService = callSessionService;
    }

    @PostMapping("/{callSessionId}/reject")
    public ResponseEntity<CallSessionResponse> reject(
            Authentication authentication,
            @PathVariable Long callSessionId) {
        return ResponseEntity.ok(callSessionService.reject(userId(authentication), callSessionId));
    }

    private static Long userId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
