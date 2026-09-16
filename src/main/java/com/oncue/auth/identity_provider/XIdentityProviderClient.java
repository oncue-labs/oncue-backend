package com.oncue.auth.identity_provider;

import com.oncue.auth.controller.request.LoginRequest;
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
public class XIdentityProviderClient implements IdentityProviderClient {

    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final String tokenUrl;
    private final String userInfoUrl;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    @Autowired
    public XIdentityProviderClient(
            RestClient.Builder restClientBuilder,
            @Value("${oncue.auth.x.token-url:https://api.x.com/2/oauth2/token}") String tokenUrl,
            @Value("${oncue.auth.x.user-info-url:https://api.x.com/2/users/me}") String userInfoUrl,
            @Value("${oncue.auth.x.client-id:}") String clientId,
            @Value("${oncue.auth.x.client-secret:}") String clientSecret,
            @Value("${oncue.auth.x.redirect-uri:}") String redirectUri) {
        this(restClientBuilder.build(), tokenUrl, userInfoUrl, clientId, clientSecret, redirectUri);
    }

    XIdentityProviderClient(
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
        return "x";
    }

    @Override
    public ExternalIdentity resolve(LoginRequest request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("code", request.authorizationCode());
        form.add("code_verifier", request.codeVerifier());
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
        Object data = userResponse == null ? null : userResponse.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            throw new IllegalStateException("Identity provider response is missing data");
        }
        return new ExternalIdentity(requiredString(dataMap, "id"));
    }

    private static void addIfPresent(MultiValueMap<String, String> form, String name, String value) {
        if (value != null && !value.isBlank()) {
            form.add(name, value);
        }
    }

    private static String requiredString(Map<?, ?> response, String fieldName) {
        if (response == null || response.get(fieldName) == null || response.get(fieldName).toString().isBlank()) {
            throw new IllegalStateException("Identity provider response is missing " + fieldName);
        }
        return response.get(fieldName).toString();
    }
}
