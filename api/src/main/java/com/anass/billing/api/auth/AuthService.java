package com.anass.billing.api.auth;

import com.anass.billing.api.exception.InvalidCredentialsException;
import com.anass.billing.api.exception.InvalidTokenException;
import com.anass.billing.api.security.JwtService;
import com.anass.billing.api.user.Usernames;
import com.anass.billing.common.user.User;
import com.anass.billing.common.user.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    // Checked when the user doesn't exist, so "unknown user" costs the same time as "wrong password"
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, LoginAttemptLimiter loginAttemptLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /**
     * The public entry point. Always goes through the failed-login limit.
     */
    public TokenResponse login(String username, String password, String clientAddress) {
        String normalized = Usernames.normalize(username);
        loginAttemptLimiter.checkAllowed(normalized, clientAddress);   // before any password work
        try {
            TokenResponse tokens = authenticate(normalized, password);
            loginAttemptLimiter.reset(normalized, clientAddress);
            return tokens;
        } catch (InvalidCredentialsException e) {
            loginAttemptLimiter.recordFailure(normalized, clientAddress);
            throw e;
        }
    }

    private TokenResponse authenticate(String normalizedUsername, String password) {
        Optional<User> found = userRepository.findByUsername(normalizedUsername);
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return new TokenResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                "Bearer",
                jwtService.accessTokenTtl().toSeconds());
    }

    public AccessTokenResponse refresh(String refreshToken) {
        Claims claims = jwtService.parseRefreshToken(refreshToken);
        User user = userRepository.findByUsername(claims.getSubject())
                .orElseThrow(InvalidTokenException::new); // user deleted since the token was issued
        return new AccessTokenResponse(
                jwtService.generateAccessToken(user),
                "Bearer",
                jwtService.accessTokenTtl().toSeconds()
        );
    }
}