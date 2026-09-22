package com.oncue.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncue.auth.controller.AuthController;
import com.oncue.auth.controller.request.LoginRequest;
import com.oncue.auth.controller.request.RefreshTokenRequest;
import com.oncue.auth.controller.response.LoginResponse;
import com.oncue.auth.service.AuthService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void loginReturnsAccessTokenExpirationAndCreationTime() throws Exception {
        var expiresAt = Instant.parse("2026-09-12T14:00:00Z");
        var createdAt = Instant.parse("2026-09-12T13:00:00Z");
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResponse("access-token", expiresAt, createdAt));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("kakao", "provider-access-token", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.expiresAt").value("2026-09-12T14:00:00Z"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-12T13:00:00Z"));
    }

    @Test
    void refreshReturnsTheRotatedTokenPair() throws Exception {
        var expiresAt = Instant.parse("2026-09-12T14:00:00Z");
        var createdAt = Instant.parse("2026-09-12T13:00:00Z");
        var refreshExpiresAt = Instant.parse("2026-10-12T13:00:00Z");
        when(authService.refresh(any(RefreshTokenRequest.class)))
                .thenReturn(new LoginResponse(
                        "new-access-token",
                        expiresAt,
                        createdAt,
                        "new-refresh-token",
                        refreshExpiresAt));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
                .andExpect(jsonPath("$.refreshTokenExpiresAt").value("2026-10-12T13:00:00Z"));
    }
}
