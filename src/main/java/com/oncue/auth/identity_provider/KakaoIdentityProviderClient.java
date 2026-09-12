package com.oncue.auth.identity_provider;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class KakaoIdentityProviderClient implements IdentityProviderClient {

    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final String tokenUrl;
    private final String userInfoUrl;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    @Autowired
    public KakaoIdentityProviderClient(
            RestClient.Builder restClientBuilder,
            @Value("${oncue.auth.kakao.token-url:https://kauth.kakao.com/oauth/token}") String tokenUrl,
            @Value("${oncue.auth.kakao.user-info-url:https://kapi.kakao.com/v2/user/me}") String userInfoUrl,
            @Value("${oncue.auth.kakao.client-id:}") String clientId,
            @Value("${oncue.auth.kakao.client-secret:}") String clientSecret,
            @Value("${oncue.auth.kakao.redirect-uri:}") String redirectUri) {
        this(restClientBuilder.build(), tokenUrl, userInfoUrl, clientId, clientSecret, redirectUri);
    }

    KakaoIdentityProviderClient(
            RestClient restClient,
            String tokenUrl,
            String userInfoUrl,
            String clientId,
            String clientSecret,
            String redirectUri) {
        this.restClient = restClient;
        this.tokenUrl = tokenUrl;
        this.userInfoUrl = userInfoUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override
    public String provider() {
        return "kakao";
    }

    @Override
    public ExternalIdentity resolve(String authorizationCode, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("code", authorizationCode);
        form.add("code_verifier", codeVerifier);
        addIfPresent(form, "client_secret", clientSecret);
        addIfPresent(form, "redirect_uri", redirectUri);

        Map<String, Object> tokenResponse = restClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(RESPONSE_TYPE);
        String accessToken = requiredString(tokenResponse, "access_token");

        Map<String, Object> userResponse = restClient.get()
                .uri(userInfoUrl)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(RESPONSE_TYPE);
        return new ExternalIdentity(requiredString(userResponse, "id"));
    }

    private static void addIfPresent(MultiValueMap<String, String> form, String name, String value) {
        if (value != null && !value.isBlank()) {
            form.add(name, value);
        }
    }

    private static String requiredString(Map<String, Object> response, String fieldName) {
        if (response == null || response.get(fieldName) == null || response.get(fieldName).toString().isBlank()) {
            throw new IllegalStateException("Identity provider response is missing " + fieldName);
        }
        return response.get(fieldName).toString();
    }
}
