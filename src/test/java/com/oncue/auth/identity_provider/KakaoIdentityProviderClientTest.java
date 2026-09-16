package com.oncue.auth.identity_provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.oncue.auth.controller.request.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoIdentityProviderClientTest {

    @Test
    void resolvesKakaoUserWithProviderAccessToken() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new KakaoIdentityProviderClient(
                restClientBuilder,
                "http://provider.test/user");
        server.expect(requestTo("http://provider.test/user"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer provider-access-token"))
                .andRespond(withSuccess("{\"id\":12345}", MediaType.APPLICATION_JSON));

        ExternalIdentity identity = client.resolve(
                new LoginRequest("kakao", "provider-access-token", null, null));

        assertThat(identity.providerUserId()).isEqualTo("12345");
        server.verify();
    }
}
