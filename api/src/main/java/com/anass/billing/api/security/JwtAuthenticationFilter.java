package com.anass.billing.api.security;

import com.anass.billing.api.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Returns the token from "Bearer <token>" (case-insensitive), or null if absent.
     */
    private static String extractBearerToken(String header) {
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        String token = header.substring(BEARER.length()).trim();
        return token.isEmpty() ? null : token;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token != null) {
            try {
                Claims claims = jwtService.parseAccessToken(token);
                String role = claims.get(JwtService.CLAIM_ROLE, String.class);
                if (role == null) {
                    throw new InvalidTokenException();
                }
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                claims.getSubject(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
            } catch (InvalidTokenException e) {
                SecurityContextHolder.clearContext();   // bad token: stay unauthenticated, the rules answer 401
            }
        }
        chain.doFilter(request, response);
    }
}