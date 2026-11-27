package com.anass.billing.api.auth;

import com.anass.billing.api.config.SecurityConfig;
import com.anass.billing.api.exception.InvalidCredentialsException;
import com.anass.billing.api.exception.InvalidTokenException;
import com.anass.billing.api.exception.TooManyLoginAttemptsException;
import com.anass.billing.api.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    AuthService authService;
    @MockBean
    JwtService jwtService;

    @Test
    void login_withValidCredentials_returnsTokens() throws Exception {
        when(authService.login("admin", "admin123", "127.0.0.1"))
                .thenReturn(new TokenResponse("acc", "ref", "Bearer", 900));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("acc"))
                .andExpect(jsonPath("$.refreshToken").value("ref"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void login_withBadCredentials_returns401() throws Exception {
        when(authService.login(any(), any(), any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }


    @Test
    void login_withBlankFields_returns400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void login_whenBlocked_returns429WithRetryAfter() throws Exception {
        when(authService.login(any(), any(), any())).thenThrow(new TooManyLoginAttemptsException(840));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"x\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "840"));
    }

    @Test
    void refresh_withAValidToken_returnsANewPair() throws Exception {
        when(authService.refresh("good")).thenReturn(new TokenResponse("new-access", "new-refresh", "Bearer", 900));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"good\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void refresh_withAnInvalidToken_returns401() throws Exception {
        when(authService.refresh("bad")).thenThrow(new InvalidTokenException());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"bad\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withABlankToken_returns400() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}