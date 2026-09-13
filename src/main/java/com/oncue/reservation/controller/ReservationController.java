package com.oncue.reservation.controller;

import com.oncue.reservation.controller.request.CreateReservationRequest;
import com.oncue.reservation.controller.request.UpdateReservationRequest;
import com.oncue.reservation.controller.response.ReservationResponse;
import com.oncue.reservation.service.ReservationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.ok(reservationService.create(userId(authentication), request));
    }

    @GetMapping
    public ResponseEntity<List<ReservationResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(reservationService.list(userId(authentication)));
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<ReservationResponse> get(
            Authentication authentication,
            @PathVariable Long reservationId) {
        return ResponseEntity.ok(reservationService.get(userId(authentication), reservationId));
    }

    @PatchMapping("/{reservationId}")
    public ResponseEntity<ReservationResponse> update(
            Authentication authentication,
            @PathVariable Long reservationId,
            @RequestBody UpdateReservationRequest request) {
        return ResponseEntity.ok(reservationService.update(userId(authentication), reservationId, request));
    }

    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<ReservationResponse> cancel(
            Authentication authentication,
            @PathVariable Long reservationId) {
        return ResponseEntity.ok(reservationService.cancel(userId(authentication), reservationId));
    }

    private static Long userId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
