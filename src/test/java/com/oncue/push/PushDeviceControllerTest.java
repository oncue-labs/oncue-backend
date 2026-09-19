package com.oncue.push;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PushDeviceController.class)
@AutoConfigureMockMvc
class PushDeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PushDeviceService pushDeviceService;

    @Test
    void replacesTheAuthenticatedUsersDeviceToken() throws Exception {
        var request = new RegisterPushDeviceRequest("device-token", "IOS", "PRODUCTION");
        var response = new PushDeviceResponse(
                "device-token",
                PushPlatform.IOS,
                PushEnvironment.PRODUCTION,
                Instant.parse("2026-09-19T00:00:00Z"),
                Instant.parse("2026-09-19T00:00:00Z"));
        when(pushDeviceService.register(eq(7L), eq(request))).thenReturn(response);

        mockMvc.perform(put("/api/v1/push-device")
                        .with(user("7"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceToken").value("device-token"))
                .andExpect(jsonPath("$.platform").value("IOS"))
                .andExpect(jsonPath("$.environment").value("PRODUCTION"));
    }

    @Test
    void deletesTheAuthenticatedUsersDeviceToken() throws Exception {
        mockMvc.perform(delete("/api/v1/push-device")
                        .with(user("7"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(pushDeviceService).delete(7L);
    }
}
