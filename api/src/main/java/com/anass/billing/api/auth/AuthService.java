package com.anass.billing.api.auth;

import com.anass.billing.api.exception.InvalidCredentialsException;
import com.anass.billing.api.exception.InvalidTokenException;
import com.anass.billing.api.security.JwtService;
import com.anass.billing.api.user.Usernames;
import com.anass.billing.common.user.RefreshToken;
import com.anass.billing.common.user.RefreshTokenRepository;
import com.anass.billing.common.user.User;
import com.anass.billing.common.user.UserRepository;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final RefreshTokenRepository refreshTokenRepository;
    // Checked when the user doesn't exist, so "unknown user" costs the same time as "wrong password"
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       LoginAttemptLimiter loginAttemptLimiter, RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.refreshTokenRepository = refreshTokenRepository;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    private static UUID tokenId(Claims claims) {
        String id = claims.getId();
        if (id == null) {
            throw new InvalidTokenException();   // issued before refresh tokens were stored
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException();
        }
    }

    /**
     * The public entry point. Always goes through the failed-login limit, and starts a new session.
     */
    public TokenResponse login(String username, String password, String clientAddress) {
        String normalized = Usernames.normalize(username);
        loginAttemptLimiter.checkAllowed(normalized, clientAddress);   // before any password work
        User user;
        try {
            user = authenticate(normalized, password);
        } catch (InvalidCredentialsException e) {
            loginAttemptLimiter.recordFailure(normalized, clientAddress);
            throw e;
        }
        loginAttemptLimiter.reset(normalized, clientAddress);
        return issueTokens(user, UUID.randomUUID());   // a new family: one login is one session
    }

    /**
     * Exchanges a refresh token for a new pair. The presented token is used up. Presenting a used-up token
     * again ends the whole session. noRollbackFor: that revocation must survive the exception we then throw.
     */
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public TokenResponse refresh(String refreshToken) {
        Claims claims = jwtService.parseRefreshToken(refreshToken);   // signature, expiry, type
        UUID jti = tokenId(claims);
        RefreshToken stored = refreshTokenRepository.findByJtiForUpdate(jti)
                .orElseThrow(InvalidTokenException::new);              // validly signed, but never issued by us
        Instant now = Instant.now();

        if (stored.isRevoked()) {
            int revoked = refreshTokenRepository.revokeFamily(stored.getFamilyId(), now);
            log.warn("Used-up refresh token presented again: session {} ended ({} live tokens revoked)",
                    stored.getFamilyId(), revoked);
            throw new InvalidTokenException();
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(InvalidTokenException::new);
        stored.revoke(now);
        return issueTokens(user, stored.getFamilyId());
    }

    private User authenticate(String normalizedUsername, String password) {
        Optional<User> found = userRepository.findByUsername(normalizedUsername);
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return user;
    }

    private TokenResponse issueTokens(User user, UUID familyId) {
        JwtService.IssuedRefreshToken refresh = jwtService.issueRefreshToken(user);
        refreshTokenRepository.save(RefreshToken.builder()
                .jti(refresh.id())
                .userId(user.getId())
                .familyId(familyId)
                .expiresAt(refresh.expiresAt())
                .build());
        return new TokenResponse(
                jwtService.generateAccessToken(user),
                refresh.token(),
                "Bearer",
                jwtService.accessTokenTtl().toSeconds());
    }
}