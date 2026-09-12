package com.oncue.auth.service;

import com.oncue.auth.controller.request.LoginRequest;
import com.oncue.auth.controller.response.LoginResponse;
import com.oncue.auth.identity_provider.ExternalIdentity;
import com.oncue.auth.identity_provider.IdentityProviderClient;
import com.oncue.auth.model.User;
import com.oncue.auth.model.UserLoginAccount;
import com.oncue.auth.repository.UserLoginAccountRepository;
import com.oncue.auth.repository.UserRepository;
import com.oncue.common.security.AccessToken;
import com.oncue.common.security.AccessTokenService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final Map<String, IdentityProviderClient> identityProviderClients;
    private final UserRepository userRepository;
    private final UserLoginAccountRepository userLoginAccountRepository;
    private final AccessTokenService accessTokenService;

    public AuthService(
            List<IdentityProviderClient> identityProviderClients,
            UserRepository userRepository,
            UserLoginAccountRepository userLoginAccountRepository,
            AccessTokenService accessTokenService) {
        this.identityProviderClients = identityProviderClients.stream()
                .collect(Collectors.toUnmodifiableMap(
                        IdentityProviderClient::provider,
                        Function.identity()));
        this.userRepository = userRepository;
        this.userLoginAccountRepository = userLoginAccountRepository;
        this.accessTokenService = accessTokenService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        IdentityProviderClient identityProviderClient = identityProviderClients.get(request.provider());
        if (identityProviderClient == null) {
            throw new IllegalArgumentException("Unsupported identity provider: " + request.provider());
        }

        ExternalIdentity externalIdentity = identityProviderClient.resolve(
                request.authorizationCode(),
                request.codeVerifier());
        UserLoginAccount existingAccount = userLoginAccountRepository
                .findByProviderAndProviderUserId(request.provider(), externalIdentity.providerUserId())
                .orElse(null);

        User user;
        if (existingAccount == null) {
            user = userRepository.save(User.active());
            userLoginAccountRepository.save(new UserLoginAccount(
                    user,
                    request.provider(),
                    externalIdentity.providerUserId()));
        } else {
            user = existingAccount.getUser();
        }

        AccessToken accessToken = accessTokenService.issue(user);
        return new LoginResponse(accessToken.value(), accessToken.expiresAt(), Instant.now());
    }
}
