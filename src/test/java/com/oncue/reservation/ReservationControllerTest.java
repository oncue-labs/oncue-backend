package com.oncue.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncue.reservation.controller.ReservationController;
import com.oncue.reservation.controller.request.CreateReservationRequest;
import com.oncue.reservation.controller.response.ReservationResponse;
import com.oncue.reservation.exception.ReservationException;
import com.oncue.reservation.service.ReservationService;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
@AutoConfigureMockMvc
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReservationService reservationService;

    @Test
    void createsReservationForAuthenticatedUser() throws Exception {
        var request = new CreateReservationRequest(
                "santa", "child-roleplay", "safe context", "safe goal",
                LocalDateTime.of(2026, 9, 8, 21, 0), "Asia/Seoul");
        var response = new ReservationResponse(
                100L, "SCHEDULED", "santa", "child-roleplay", "safe context", "safe goal",
                request.scheduledAtLocal(), request.timeZone(), Instant.parse("2026-09-08T12:00:00Z"),
                Instant.parse("2026-09-08T11:55:00Z"), Instant.parse("2026-09-08T10:00:00Z"), null, null);
        when(reservationService.create(eq(7L), any(CreateReservationRequest.class))).thenReturn(response);

                mockMvc.perform(post("/api/v1/reservations")
                        .with(user("7"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(100))
                .andExpect(jsonPath("$.reservationStatus").value("SCHEDULED"))
                .andExpect(jsonPath("$.scheduledAtUtc").value("2026-09-08T12:00:00Z"));
    }

    @Test
    void includesRequestIdAndCreatedAtInReservationErrors() throws Exception {
        var request = new CreateReservationRequest(
                "santa", "child-roleplay", "unsafe context", "unsafe goal",
                LocalDateTime.of(2026, 9, 8, 21, 0), "Asia/Seoul");
        when(reservationService.create(eq(7L), any(CreateReservationRequest.class)))
                .thenThrow(new ReservationException(HttpStatus.UNPROCESSABLE_ENTITY, "Reservation rejected"));

        mockMvc.perform(post("/api/v1/reservations")
                        .with(user("7"))
                        .with(csrf())
                        .header("X-Request-Id", "request-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Request-Id", "request-123"))
                .andExpect(jsonPath("$.message").value("Reservation rejected"))
                .andExpect(jsonPath("$.requestId").value("request-123"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }
}
