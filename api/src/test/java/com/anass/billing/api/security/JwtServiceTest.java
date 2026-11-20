package com.anass.billing.api.security;

import com.anass.billing.common.user.Role;
import com.anass.billing.common.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";
    private final JwtProperties properties = new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(7));
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private final JwtService jwtService = new JwtService(properties);
    private final User admin = User.builder().username("admin").passwordHash("x").role(Role.ADMIN).build();

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    @Test
    void accessToken_carriesSubjectRoleAndType_andExpiresInFifteenMinutes() {
        Claims claims = parse(jwtService.generateAccessToken(admin));

        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(claims.get(JwtService.CLAIM_ROLE)).isEqualTo("ADMIN");
        assertThat(claims.get(JwtService.CLAIM_TYPE)).isEqualTo("access");
        assertThat(Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()))
                .isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void refreshToken_isTypedAsRefresh_andLivesSevenDays() {
        Claims claims = parse(jwtService.generateRefreshToken(admin));

        assertThat(claims.get(JwtService.CLAIM_TYPE)).isEqualTo("refresh");
        assertThat(Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()))
                .isEqualTo(Duration.ofDays(7));
    }

    @Test
    void tokenSignedWithAnotherSecret_isRejected() {
        SecretKey other = Keys.hmacShaKeyFor("a-completely-different-secret-value-99".getBytes(StandardCharsets.UTF_8));
        String token = jwtService.generateAccessToken(admin);

        assertThatThrownBy(() -> Jwts.parser().verifyWith(other).build().parseSignedClaims(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void secretShorterThan32Bytes_failsAtStartup() {
        JwtProperties weak = new JwtProperties("too-short", Duration.ofMinutes(15), Duration.ofDays(7));

        assertThatThrownBy(() -> new JwtService(weak)).isInstanceOf(IllegalStateException.class);
    }
}