package com.oncue.auth.identity_provider;

import com.oncue.auth.controller.request.LoginRequest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KakaoIdentityProviderClient implements IdentityProviderClient {

    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final String userInfoUrl;

    @Autowired
    public KakaoIdentityProviderClient(
            RestClient.Builder restClientBuilder,
            @Value("${oncue.auth.kakao.user-info-url:https://kapi.kakao.com/v2/user/me}") String userInfoUrl) {
        this(restClientBuilder.build(), userInfoUrl);
    }

    KakaoIdentityProviderClient(
            RestClient restClient,
            String userInfoUrl) {
        this.restClient = restClient;
        this.userInfoUrl = userInfoUrl;
    }

    @Override
    public String provider() {
        return "kakao";
    }

    @Override
    public ExternalIdentity resolve(LoginRequest request) {
        Map<String, Object> userResponse = restClient.get()
                .uri(userInfoUrl)
                .headers(headers -> headers.setBearerAuth(request.providerAccessToken()))
                .retrieve()
                .body(RESPONSE_TYPE);
        return new ExternalIdentity(requiredString(userResponse, "id"));
    }

    private static String requiredString(Map<String, Object> response, String fieldName) {
        if (response == null || response.get(fieldName) == null || response.get(fieldName).toString().isBlank()) {
            throw new IllegalStateException("Identity provider response is missing " + fieldName);
        }
        return response.get(fieldName).toString();
    }
}
