package com.anass.billing.common.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
public class UserRepositoryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    @Autowired
    UserRepository userRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void connectToContainer(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void seededAdmin_hasAdminRole_andTheDevPassword() {
        User admin = userRepository.findByUsername("admin").orElseThrow();

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(encoder.matches("admin123", admin.getPasswordHash())).isTrue();
        assertThat(admin.getPasswordHash()).doesNotContain("admin123");
    }

    @Test
    void seededMerchant_hasMerchantRole_andTheDevPassword() {
        User merchant = userRepository.findByUsername("merchant").orElseThrow();

        assertThat(merchant.getRole()).isEqualTo(merchant.getRole());
        assertThat(encoder.matches("merchant123", merchant.getPasswordHash())).isTrue();
        assertThat(merchant.getPasswordHash()).doesNotContain("merchant123");
    }

    @Test
    void newUser_roundTrips() {
        userRepository.saveAndFlush(User.builder()
                .username("alice").passwordHash(encoder.encode("s3cret")).role(Role.MERCHANT).build());
        User found = userRepository.findByUsername("alice").orElseThrow();
        assertThat(found.getId()).isNotNull();
        assertThat(found.getRole()).isEqualTo(Role.MERCHANT);
    }

    @Test
    void unknownUsername_isEmpty() {
        assertThat(userRepository.findByUsername("nobody")).isEmpty();
    }

    @Test
    void duplicateUsername_isRejectedByTheDatabase() {
        assertThatThrownBy(() -> userRepository.saveAndFlush(User.builder()
                .username("admin").passwordHash("x").role(Role.MERCHANT).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownRole_isRejectedByTheDatabase() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (id, username, password_hash, role) VALUES (gen_random_uuid(), 'bob', 'x', 'ROOT')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByUsername_isTrueForSeededUsers_andFalseOtherwise() {
        assertThat(userRepository.existsByUsername("admin")).isTrue();
        assertThat(userRepository.existsByUsername("nobody")).isFalse();
    }

    @Test
    void mixedCaseUsername_isRejectedByTheDatabase() {
        assertThatThrownBy(() -> userRepository.saveAndFlush(User.builder()
                .username("Alice").passwordHash("x").role(Role.MERCHANT).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
