package com.oncue.auth.identity_provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class XIdentityProviderClientTest {

    @Test
    void exchangesAuthorizationCodeAndCodeVerifierThenResolvesXUser() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new XIdentityProviderClient(
                restClientBuilder,
                "http://provider.test/token",
                "http://provider.test/user",
                "x-client",
                "x-secret",
                "https://app.test/callback");

        server.expect(requestTo("http://provider.test/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code=authorization-code")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code_verifier=code-verifier")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"provider-access-token\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://provider.test/user"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\":{\"id\":\"x-user-1\"}}", MediaType.APPLICATION_JSON));

        ExternalIdentity identity = client.resolve("authorization-code", "code-verifier");

        assertThat(identity.providerUserId()).isEqualTo("x-user-1");
        server.verify();
    }
}
