package com.oncue.call;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CallSessionController.class)
@AutoConfigureMockMvc
class CallSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CallSessionService callSessionService;

    @Test
    void rejectsCallSessionForAuthenticatedUser() throws Exception {
        when(callSessionService.reject(eq(7L), eq(42L))).thenReturn(
                new CallSessionResponse(
                        42L,
                        CallStatus.RINGING,
                        CallOutcome.FAILED,
                        Instant.parse("2026-09-17T12:00:00Z"),
                        Instant.parse("2026-09-17T12:00:30Z")));

        mockMvc.perform(post("/api/v1/call-sessions/42/reject")
                        .with(user("7"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callSessionId").value(42))
                .andExpect(jsonPath("$.callStatus").value("RINGING"))
                .andExpect(jsonPath("$.callOutcome").value("FAILED"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-17T12:00:00Z"))
                .andExpect(jsonPath("$.endedAt").value("2026-09-17T12:00:30Z"));
    }
}
