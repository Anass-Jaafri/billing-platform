package com.anass.billing.api.security;

import com.anass.billing.api.exception.InvalidTokenException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerToken_authenticatesWithTheRoleAuthority() throws Exception {
        request.addHeader("Authorization", "Bearer good");
        when(jwtService.parseAccessToken("good"))
                .thenReturn(Jwts.claims().subject("admin").add("role", "ADMIN").build());

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getName()).isEqualTo("admin");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
        verify(chain).doFilter(request, response);
    }

    @Test
    void noAuthorizationHeader_staysUnauthenticated_andContinuesTheChain() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtService);
    }

    @Test
    void invalidToken_staysUnauthenticated_andContinuesTheChain() throws Exception {
        request.addHeader("Authorization", "Bearer bad");
        when(jwtService.parseAccessToken("bad")).thenThrow(new InvalidTokenException());

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void nonBearerScheme_isIgnored() throws Exception {
        request.addHeader("Authorization", "Basic YWRtaW46YWRtaW4=");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void tokenWithoutARoleClaim_staysUnauthenticated() throws Exception {
        request.addHeader("Authorization", "Bearer norole");
        when(jwtService.parseAccessToken("norole")).thenReturn(Jwts.claims().subject("admin").build());

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
