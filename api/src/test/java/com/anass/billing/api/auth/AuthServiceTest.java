package com.anass.billing.api.auth;

import com.anass.billing.api.exception.InvalidCredentialsException;
import com.anass.billing.api.exception.InvalidTokenException;
import com.anass.billing.api.security.JwtService;
import com.anass.billing.common.user.Role;
import com.anass.billing.common.user.User;
import com.anass.billing.common.user.UserRepository;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    @Mock
    UserRepository userRepository;
    @Mock
    JwtService jwtService;
    private AuthService authService;
    private User admin;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, encoder, jwtService);
        admin = User.builder().username("admin").passwordHash(encoder.encode("admin123")).role(Role.ADMIN).build();
    }

    @Test
    void correctCredentials_returnBothTokens() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(jwtService.generateAccessToken(admin)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(admin)).thenReturn("refresh-token");
        when(jwtService.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));

        TokenResponse response = authService.login("admin", "admin123");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
    }

    @Test
    void wrongPassword_isRejected_andIssuesNoToken() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> authService.login("admin", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(jwtService);
    }

    @Test
    void unknownUser_isRejectedWithTheSameException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost", "whatever"))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(jwtService);
    }

    @Test
    void refresh_withAValidToken_returnsANewAccessToken() {
        when(jwtService.parseRefreshToken("good")).thenReturn(Jwts.claims().subject("admin").build());
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(jwtService.generateAccessToken(admin)).thenReturn("new-access");
        when(jwtService.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));

        AccessTokenResponse response = authService.refresh("good");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.expiresIn()).isEqualTo(900);
    }

    @Test
    void refresh_whenTheUserNoLongerExists_isRejected() {
        when(jwtService.parseRefreshToken("good")).thenReturn(Jwts.claims().subject("ghost").build());
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("good")).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refresh_withAnInvalidToken_isRejected_andIssuesNothing() {
        when(jwtService.parseRefreshToken("bad")).thenThrow(new InvalidTokenException());

        assertThatThrownBy(() -> authService.refresh("bad")).isInstanceOf(InvalidTokenException.class);
        verify(jwtService, never()).generateAccessToken(any());
    }
}