package com.oncue.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletResponse;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AccessTokenAuthenticationFilterTest {

    @Mock
    private AccessTokenService accessTokenService;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesRequestWithValidBearerToken() throws Exception {
        when(accessTokenService.userId("valid-token")).thenReturn(Optional.of(42L));
        var filter = new AccessTokenAuthenticationFilter(accessTokenService);
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo("42");
        verify(filterChain).doFilter(same(request), any(ServletResponse.class));
    }

    @Test
    void leavesRequestUnauthenticatedWhenBearerTokenIsInvalid() throws Exception {
        when(accessTokenService.userId("invalid-token")).thenReturn(Optional.empty());
        var filter = new AccessTokenAuthenticationFilter(accessTokenService);
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
