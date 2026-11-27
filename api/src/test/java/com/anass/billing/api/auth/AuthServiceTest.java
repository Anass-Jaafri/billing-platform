package com.anass.billing.api.auth;

import com.anass.billing.api.exception.InvalidCredentialsException;
import com.anass.billing.api.exception.InvalidTokenException;
import com.anass.billing.api.exception.TooManyLoginAttemptsException;
import com.anass.billing.api.security.JwtService;
import com.anass.billing.common.user.*;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final UUID FAMILY = UUID.randomUUID();
    private static final UUID OLD_JTI = UUID.randomUUID();
    private static final UUID NEW_JTI = UUID.randomUUID();
    private static final Instant EXPIRY = Instant.parse("2030-01-08T00:00:00Z");
    private static final String ADDRESS = "10.0.0.1";

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    @Mock
    UserRepository userRepository;
    @Mock
    JwtService jwtService;
    @Mock
    LoginAttemptLimiter limiter;
    @Mock
    RefreshTokenRepository refreshTokenRepository;
    private AuthService authService;
    private User admin;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, encoder, jwtService, limiter, refreshTokenRepository);
        admin = User.builder().id(UUID.randomUUID()).username("admin")
                .passwordHash(encoder.encode("admin123")).role(Role.ADMIN).build();
    }

    private void stubTokenIssuing(UUID jti, String refreshToken) {
        when(jwtService.generateAccessToken(admin)).thenReturn("access-token");
        when(jwtService.issueRefreshToken(admin))
                .thenReturn(new JwtService.IssuedRefreshToken(refreshToken, jti, EXPIRY));
        when(jwtService.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
    }

    private RefreshToken storedToken(UUID jti, boolean revoked) {
        return RefreshToken.builder().jti(jti).userId(admin.getId()).familyId(FAMILY).expiresAt(EXPIRY)
                .revokedAt(revoked ? Instant.now().minusSeconds(60) : null).build();
    }

    private void presenting(UUID jti) {
        when(jwtService.parseRefreshToken("presented"))
                .thenReturn(Jwts.claims().id(jti.toString()).subject("admin").build());
    }

    private RefreshToken savedToken() {
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        return saved.getValue();
    }

    // ---------- login ----------

    @Test
    void correctCredentials_returnBothTokens_andRememberTheRefreshToken() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        stubTokenIssuing(NEW_JTI, "refresh-token");

        TokenResponse response = authService.login("admin", "admin123", ADDRESS);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        RefreshToken saved = savedToken();
        assertThat(saved.getJti()).isEqualTo(NEW_JTI);
        assertThat(saved.getUserId()).isEqualTo(admin.getId());
        assertThat(saved.getFamilyId()).isNotNull();
        assertThat(saved.getExpiresAt()).isEqualTo(EXPIRY);
        assertThat(saved.isRevoked()).isFalse();
    }

    @Test
    void everyLogin_startsItsOwnSession() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(jwtService.generateAccessToken(admin)).thenReturn("access-token");
        when(jwtService.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
        when(jwtService.issueRefreshToken(admin))
                .thenReturn(new JwtService.IssuedRefreshToken("one", UUID.randomUUID(), EXPIRY))
                .thenReturn(new JwtService.IssuedRefreshToken("two", UUID.randomUUID(), EXPIRY));

        authService.login("admin", "admin123", ADDRESS);
        authService.login("admin", "admin123", ADDRESS);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getFamilyId())
                .isNotEqualTo(saved.getAllValues().get(1).getFamilyId());
    }

    @Test
    void usernameIsTrimmedAndLowercased_beforeTheLookup() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        stubTokenIssuing(NEW_JTI, "refresh-token");

        authService.login("  Admin ", "admin123", ADDRESS);

        verify(userRepository).findByUsername("admin");
    }

    @Test
    void wrongPassword_isRejected_andIssuesNoToken() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> authService.login("admin", "wrong", ADDRESS))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(jwtService, refreshTokenRepository);
    }

    @Test
    void unknownUser_isRejectedWithTheSameException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost", "whatever", ADDRESS))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(jwtService, refreshTokenRepository);
    }

    @Test
    void login_fromABlockedAddress_isRefusedBeforeAnyPasswordWork() {
        doThrow(new TooManyLoginAttemptsException(60)).when(limiter).checkAllowed("admin", ADDRESS);

        assertThatThrownBy(() -> authService.login("Admin", "admin123", ADDRESS))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        verifyNoInteractions(userRepository, jwtService, refreshTokenRepository);
    }

    @Test
    void login_countsAFailure_andASuccessClearsTheCounter() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> authService.login("admin", "wrong", ADDRESS))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(limiter).recordFailure("admin", ADDRESS);
        verify(limiter, never()).reset(any(), any());

        stubTokenIssuing(NEW_JTI, "refresh-token");
        authService.login("admin", "admin123", ADDRESS);
        verify(limiter).reset("admin", ADDRESS);
    }

    // ---------- refresh ----------

    @Test
    void refresh_withALiveToken_usesItUp_andIssuesTheNextOneInTheSameSession() {
        RefreshToken stored = storedToken(OLD_JTI, false);
        presenting(OLD_JTI);
        when(refreshTokenRepository.findByJtiForUpdate(OLD_JTI)).thenReturn(Optional.of(stored));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        stubTokenIssuing(NEW_JTI, "new-refresh");

        TokenResponse response = authService.refresh("presented");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        assertThat(stored.isRevoked()).isTrue();
        RefreshToken next = savedToken();
        assertThat(next.getJti()).isEqualTo(NEW_JTI);
        assertThat(next.getFamilyId()).isEqualTo(FAMILY);
    }

    @Test
    void refresh_withAUsedUpToken_endsTheWholeSession() {
        presenting(OLD_JTI);
        when(refreshTokenRepository.findByJtiForUpdate(OLD_JTI)).thenReturn(Optional.of(storedToken(OLD_JTI, true)));

        assertThatThrownBy(() -> authService.refresh("presented")).isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenRepository).revokeFamily(eq(FAMILY), any(Instant.class));
        verify(jwtService, never()).issueRefreshToken(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_withATokenWeNeverIssued_isRejected_andRevokesNothing() {
        presenting(OLD_JTI);
        when(refreshTokenRepository.findByJtiForUpdate(OLD_JTI)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("presented")).isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenRepository, never()).revokeFamily(any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_withATokenThatHasNoId_isRejected() {
        when(jwtService.parseRefreshToken("presented")).thenReturn(Jwts.claims().subject("admin").build());

        assertThatThrownBy(() -> authService.refresh("presented")).isInstanceOf(InvalidTokenException.class);
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void refresh_whenTheUserNoLongerExists_isRejected() {
        presenting(OLD_JTI);
        when(refreshTokenRepository.findByJtiForUpdate(OLD_JTI)).thenReturn(Optional.of(storedToken(OLD_JTI, false)));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("presented")).isInstanceOf(InvalidTokenException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_withAnInvalidToken_isRejected_andTouchesNothing() {
        when(jwtService.parseRefreshToken("bad")).thenThrow(new InvalidTokenException());

        assertThatThrownBy(() -> authService.refresh("bad")).isInstanceOf(InvalidTokenException.class);
        verifyNoInteractions(refreshTokenRepository);
        verify(jwtService, never()).issueRefreshToken(any());
    }
}