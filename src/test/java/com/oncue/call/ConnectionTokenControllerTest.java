package com.oncue.call;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConnectionTokenController.class)
@AutoConfigureMockMvc
class ConnectionTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ConnectionTokenService connectionTokenService;

    @Test
    void issuesConnectionTokenForAuthenticatedCallSessionOwner() throws Exception {
        ConnectionTokenResponse response = new ConnectionTokenResponse(
                "signed-token",
                "wss://voice.example.com/v1/signaling/call-sessions/42",
                List.of(new ConnectionTokenResponse.IceServer(
                        List.of("stun:stun.example.com"), null, null)),
                Instant.parse("2026-09-13T00:01:00Z"),
                Instant.parse("2026-09-13T00:00:00Z"));
        when(connectionTokenService.issue(eq(7L), eq(42L))).thenReturn(response);

        mockMvc.perform(post("/api/v1/call-sessions/42/connection-token")
                        .with(user("7"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionToken").value("signed-token"))
                .andExpect(jsonPath("$.signalingUrl")
                        .value("wss://voice.example.com/v1/signaling/call-sessions/42"))
                .andExpect(jsonPath("$.iceServers[0].urls[0]").value("stun:stun.example.com"))
                .andExpect(jsonPath("$.expiresAt").value("2026-09-13T00:01:00Z"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-13T00:00:00Z"));
    }
}
