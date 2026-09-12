package com.oncue.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "oncue.auth.signing-secret=test-signing-secret-for-security-context")
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void nonAuthApiRequiresBearerAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/protected-resource"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginEndpointIsPublicBeforeRequestValidation() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed());
    }
}
