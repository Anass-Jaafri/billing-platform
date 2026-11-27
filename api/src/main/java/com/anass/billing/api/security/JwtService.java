package com.anass.billing.api.security;

import com.anass.billing.api.exception.InvalidTokenException;
import com.anass.billing.common.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
@EnableConfigurationProperties(JwtProperties.class)
public class JwtService {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    private static final int MIN_SECRET_BYTES = 32;   // 256 bits, what HS256 needs

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey key;

    @Autowired
    public JwtService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtService(JwtProperties properties, Clock clock) {
        byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET (app.jwt.secret) must be set to a random value of at least "
                            + MIN_SECRET_BYTES + " bytes. Generate one, for example, with: openssl rand -base64 48");
        }
        this.properties = properties;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String generateAccessToken(User user) {
        return build(user, TYPE_ACCESS, properties.accessTtl());
    }

    public String generateRefreshToken(User user) {
        return build(user, TYPE_REFRESH, properties.refreshTtl());
    }

    public Duration accessTokenTtl() {
        return properties.accessTtl();
    }

    /**
     * Verifies signature and expiry, and that this really is an access token.
     */
    public Claims parseAccessToken(String token) {
        Claims claims = parse(token);
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE))) {
            throw new InvalidTokenException();
        }
        return claims;
    }

    /**
     * Verifies signature and expiry, and that this really is a refresh token.
     */
    public Claims parseRefreshToken(String token) {
        Claims claims = parse(token);
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE))) {
            throw new InvalidTokenException();
        }
        return claims;
    }


    private Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key) // rejects wrong signature and unsigned ("alg: none") tokens
                    .clock(() -> Date.from(clock.instant())) // expiry is judged by our clock, so tests control time
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) { // malformed, expired, bad signature, blank
            throw new InvalidTokenException();
        }
    }

    private String build(User user, String type, Duration ttl) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}