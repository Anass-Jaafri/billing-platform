package com.anass.billing.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthFlowIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Value("${app.jwt.secret}")
    String jwtSecret;

    @DynamicPropertySource
    static void connectAppToContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    }

    // ---------- login ----------

    @Test
    void login_withTheSeededAdmin_returnsTokens_thatOpenProtectedEndpoints() throws Exception {
        JsonNode tokens = login("admin", "admin123");

        assertThat(tokens.get("accessToken").asText()).isNotBlank();
        assertThat(tokens.get("refreshToken").asText()).isNotBlank();
        assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(tokens.get("expiresIn").asLong()).isEqualTo(900);

        get("/charges", tokens.get("accessToken").asText()).andExpect(status().isOk());
    }

    @Test
    void login_failures_lookIdentical_forWrongPasswordAndUnknownUser() throws Exception {
        String wrongPassword = loginRequest("admin", "wrong-password")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownUser = loginRequest("ghost", "whatever")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(wrongPassword).get("message").asText())
                .isEqualTo(objectMapper.readTree(unknownUser).get("message").asText())
                .isEqualTo("Invalid username or password");
    }

    // ---------- refresh ----------

    @Test
    void refresh_exchangesTheRefreshTokenForAWorkingAccessToken() throws Exception {
        String refreshToken = login("admin", "admin123").get("refreshToken").asText();

        String body = refreshRequest(refreshToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        get("/charges", objectMapper.readTree(body).get("accessToken").asText()).andExpect(status().isOk());
    }

    @Test
    void refresh_rejectsAnAccessToken() throws Exception {
        String accessToken = login("admin", "admin123").get("accessToken").asText();

        refreshRequest(accessToken).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoints_rejectARefreshTokenUsedAsBearer() throws Exception {
        String refreshToken = login("admin", "admin123").get("refreshToken").asText();

        get("/charges", refreshToken).andExpect(status().isUnauthorized());
    }

    // ---------- bad tokens ----------

    @Test
    void expiredAccessToken_isRejected() throws Exception {
        String expired = signedToken(jwtSecret, "access", Instant.now().minus(Duration.ofHours(1)), Duration.ofMinutes(15));

        get("/charges", expired).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredRefreshToken_isRejectedAtRefresh() throws Exception {
        String expired = signedToken(jwtSecret, "refresh", Instant.now().minus(Duration.ofDays(8)), Duration.ofDays(7));

        refreshRequest(expired).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenClaimingAdmin_butSignedWithAnotherKey_isRejected() throws Exception {
        String forged = signedToken("an-attackers-own-secret-an-attackers-own-secret", "access",
                Instant.now(), Duration.ofMinutes(15));

        get("/charges", forged).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withoutAToken_orWithGarbage_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/charges")).andExpect(status().isUnauthorized());
        get("/charges", "not-a-jwt").andExpect(status().isUnauthorized());
    }

    // ---------- roles ----------

    @Test
    void adminArea_isAnonymous401_merchant403_admin_passesSecurity() throws Exception {
        String merchantToken = login("merchant", "merchant123").get("accessToken").asText();
        String adminToken = login("admin", "admin123").get("accessToken").asText();

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/dlq/shipping"))
                .andExpect(status().isUnauthorized());
        get("/admin/dlq/shipping", merchantToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        // an admin gets past security; the controller then answers 404 for the unknown worker
        get("/admin/dlq/shipping", adminToken).andExpect(status().isNotFound());
    }

    @Test
    void merchant_canUseNormalEndpoints_butCannotCreateUsers() throws Exception {
        String merchantToken = login("merchant", "merchant123").get("accessToken").asText();

        get("/charges", merchantToken).andExpect(status().isOk());
        createUser(merchantToken, uniqueUsername(), "MERCHANT").andExpect(status().isForbidden());
    }

    // ---------- user management, end to end ----------

    @Test
    void adminCreatesAUser_whoCanLogIn_butIsNotAnAdmin() throws Exception {
        String adminToken = login("admin", "admin123").get("accessToken").asText();
        String username = uniqueUsername();

        createUser(adminToken, username, "MERCHANT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("MERCHANT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String newUserToken = login(username, "s3cretpass").get("accessToken").asText();
        get("/charges", newUserToken).andExpect(status().isOk());
        createUser(newUserToken, uniqueUsername(), "ADMIN").andExpect(status().isForbidden());
    }

    @Test
    void creatingAUserWithATakenUsername_returns409() throws Exception {
        String adminToken = login("admin", "admin123").get("accessToken").asText();

        createUser(adminToken, "merchant", "MERCHANT")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username already exists: merchant"));
    }

    // ---------- public endpoints ----------

    @Test
    void healthEndpoint_isPublic() throws Exception {
        int status = mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isNotIn(401, 403);   // reachable without a token (UP/DOWN is not this test's concern)
    }

    // ---------- helpers ----------

    private ResultActions loginRequest(String username, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))));
    }

    private JsonNode login(String username, String password) throws Exception {
        String body = loginRequest(username, password).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private ResultActions refreshRequest(String refreshToken) throws Exception {
        return mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }

    private ResultActions get(String path, String token) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(path).header("Authorization", "Bearer " + token));
    }

    private ResultActions createUser(String token, String username, String role) throws Exception {
        return mockMvc.perform(post("/admin/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("username", username, "password", "s3cretpass", "role", role))));
    }

    private String uniqueUsername() {
        return "it" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Builds a token exactly like JwtService does, but with a lifetime and key the test controls.
     */
    private String signedToken(String secret, String type, Instant issuedAt, Duration lifetime) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("admin")
                .claim("role", "ADMIN")
                .claim("type", type)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(lifetime)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}