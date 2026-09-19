package com.oncue.push;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/push-device")
public class PushDeviceController {

    private final PushDeviceService pushDeviceService;

    public PushDeviceController(PushDeviceService pushDeviceService) {
        this.pushDeviceService = pushDeviceService;
    }

    @PutMapping
    public ResponseEntity<PushDeviceResponse> register(
            Authentication authentication,
            @Valid @RequestBody RegisterPushDeviceRequest request) {
        return ResponseEntity.ok(pushDeviceService.register(userId(authentication), request));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(Authentication authentication) {
        pushDeviceService.delete(userId(authentication));
        return ResponseEntity.noContent().build();
    }

    private static Long userId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
