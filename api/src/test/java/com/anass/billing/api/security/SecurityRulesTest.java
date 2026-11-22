package com.anass.billing.api.security;

import com.anass.billing.api.config.SecurityConfig;
import com.anass.billing.common.user.Role;
import com.anass.billing.common.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SecurityRulesTest.Probe.class)
@Import({SecurityConfig.class, JwtService.class, SecurityRulesTest.Probe.class})
@TestPropertySource(properties = {
        "app.jwt.secret=security-rules-test-secret-security-rules-123",
        "app.jwt.access-ttl=15m",
        "app.jwt.refresh-ttl=7d"})
class SecurityRulesTest {

    private final User admin = User.builder().username("admin").passwordHash("x").role(Role.ADMIN).build();
    private final User merchant = User.builder().username("merchant").passwordHash("x").role(Role.MERCHANT).build();
    @Autowired
    MockMvc mockMvc;
    @Autowired
    JwtService jwtService;

    @Test
    void protectedEndpoint_withoutToken_returns401Json() throws Exception {
        mockMvc.perform(get("/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void protectedEndpoint_withValidAccessToken_returns200() throws Exception {
        mockMvc.perform(get("/probe").header("Authorization", "Bearer " + jwtService.generateAccessToken(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_withARefreshTokenAsBearer_returns401() throws Exception {
        mockMvc.perform(get("/probe").header("Authorization", "Bearer " + jwtService.generateRefreshToken(admin)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withGarbageToken_returns401() throws Exception {
        mockMvc.perform(get("/probe").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authEndpoints_needNoToken() throws Exception {
        mockMvc.perform(post("/auth/probe")).andExpect(status().isOk());
    }

    @Test
    void providerWebhook_needsNoToken_butReadingWebhooksDoes() throws Exception {
        mockMvc.perform(post("/webhooks/payment-provider")).andExpect(status().isOk());
        mockMvc.perform(get("/webhooks")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/admin/probe")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_asMerchant_returns403Json() throws Exception {
        mockMvc.perform(get("/admin/probe").header("Authorization", "Bearer " + jwtService.generateAccessToken(merchant)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));
    }

    @Test
    void adminEndpoint_asAdmin_returns200() throws Exception {
        mockMvc.perform(get("/admin/probe").header("Authorization", "Bearer " + jwtService.generateAccessToken(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void merchantCanStillUseNormalProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/probe").header("Authorization", "Bearer " + jwtService.generateAccessToken(merchant)))
                .andExpect(status().isOk());
    }

    @RestController
    static class Probe {
        @GetMapping("/probe")
        String protectedEndpoint() {
            return "ok";
        }

        @PostMapping("/auth/probe")
        String authEndpoint() {
            return "ok";
        }

        @PostMapping("/webhooks/payment-provider")
        String providerWebhook() {
            return "ok";
        }

        @GetMapping("/webhooks")
        String webhookList() {
            return "ok";
        }

        @GetMapping("/admin/probe")
        String adminEndpoint() {
            return "ok";
        }
    }
}