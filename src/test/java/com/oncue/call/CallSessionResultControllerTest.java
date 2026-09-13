package com.oncue.call;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncue.common.security.AccessTokenService;
import com.oncue.common.security.SecurityConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CallSessionResultController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
@TestPropertySource(properties = "oncue.voice.service-token=test-service-token")
class CallSessionResultControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CallSessionService callSessionService;

    @MockBean
    private AccessTokenService accessTokenService;

    @Test
    void acceptsVoiceResultWithInternalServiceToken() throws Exception {
        CallSessionResultRequest request = new CallSessionResultRequest(
                "voice-session-321",
                CallStatus.IN_CALL,
                CallOutcome.SUCCEEDED,
                Instant.parse("2026-09-13T00:00:10Z"),
                Instant.parse("2026-09-13T00:01:10Z"));

        mockMvc.perform(post("/internal/v1/call-sessions/321/result")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-service-token")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(callSessionService).applyResult(new CallResult(
                321L,
                "voice-session-321",
                CallStatus.IN_CALL,
                CallOutcome.SUCCEEDED,
                Instant.parse("2026-09-13T00:00:10Z"),
                Instant.parse("2026-09-13T00:01:10Z")));
    }

    @Test
    void rejectsMissingOrInvalidInternalServiceToken() throws Exception {
        CallSessionResultRequest request = new CallSessionResultRequest(
                "voice-session-321", CallStatus.RINGING, CallOutcome.FAILED, null, null);
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/internal/v1/call-sessions/321/result")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/internal/v1/call-sessions/321/result")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        verify(callSessionService, never()).applyResult(any(CallResult.class));
    }
}
