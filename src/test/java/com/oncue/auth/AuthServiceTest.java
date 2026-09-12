package com.oncue.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oncue.auth.identity_provider.ExternalIdentity;
import com.oncue.auth.identity_provider.IdentityProviderClient;
import com.oncue.auth.model.User;
import com.oncue.auth.model.UserLoginAccount;
import com.oncue.auth.repository.UserLoginAccountRepository;
import com.oncue.auth.repository.UserRepository;
import com.oncue.auth.controller.request.LoginRequest;
import com.oncue.auth.controller.response.LoginResponse;
import com.oncue.auth.service.AuthService;
import com.oncue.common.security.AccessToken;
import com.oncue.common.security.AccessTokenService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private IdentityProviderClient identityProviderClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserLoginAccountRepository userLoginAccountRepository;

    @Mock
    private AccessTokenService accessTokenService;

    @Test
    void loginWithKakaoCreatesUserAndReturnsContractFields() {
        var request = new LoginRequest("kakao", "kakao-authorization-code", "kakao-code-verifier");
        var user = new User(42L);
        var expiresAt = Instant.parse("2026-09-12T14:00:00Z");

        when(identityProviderClient.provider()).thenReturn("kakao");
        when(identityProviderClient.resolve("kakao-authorization-code", "kakao-code-verifier"))
                .thenReturn(new ExternalIdentity("kakao-user-1"));
        when(userLoginAccountRepository.findByProviderAndProviderUserId("kakao", "kakao-user-1"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(accessTokenService.issue(user)).thenReturn(new AccessToken("access-token", expiresAt));

        var authService = newAuthService();
        LoginResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.createdAt()).isNotNull();
        verify(userLoginAccountRepository).save(any(UserLoginAccount.class));
    }

    @Test
    void loginWithExistingAccountReusesLinkedUser() {
        var request = new LoginRequest("x", "x-authorization-code", "x-code-verifier");
        var user = new User(7L);
        var account = new UserLoginAccount(user, "x", "x-user-1");
        var expiresAt = Instant.parse("2026-09-12T14:00:00Z");

        when(identityProviderClient.provider()).thenReturn("x");
        when(identityProviderClient.resolve("x-authorization-code", "x-code-verifier"))
                .thenReturn(new ExternalIdentity("x-user-1"));
        when(userLoginAccountRepository.findByProviderAndProviderUserId("x", "x-user-1"))
                .thenReturn(Optional.of(account));
        when(accessTokenService.issue(user)).thenReturn(new AccessToken("access-token", expiresAt));

        var authService = newAuthService();
        LoginResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void loginRejectsUnsupportedProvider() {
        when(identityProviderClient.provider()).thenReturn("kakao");
        var authService = newAuthService();

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("apple", "authorization-code", "code-verifier")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AuthService newAuthService() {
        return new AuthService(
                List.of(identityProviderClient),
                userRepository,
                userLoginAccountRepository,
                accessTokenService);
    }
}
