package br.com.fiapx.videoapi.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateWhenBearerTokenIsValid() throws Exception {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService);
        FilterChain chain = mock(FilterChain.class);
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "user@fiapx.com");
        when(jwtTokenService.validateToken("jwt-token")).thenReturn(true);
        when(jwtTokenService.getAuthenticatedUser("jwt-token")).thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer jwt-token");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(user);
        verify(chain).doFilter(eq(request), any());
    }

    @Test
    void shouldIgnoreMissingOrInvalidToken() throws Exception {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(eq(request), any());
    }
}
