package com.anass.billing.api.user;

import com.anass.billing.api.exception.UsernameAlreadyExistsException;
import com.anass.billing.common.user.Role;
import com.anass.billing.common.user.User;
import com.anass.billing.common.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    @Mock
    UserRepository userRepository;
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, encoder);
    }

    @Test
    void createUser_storesAHash_neverThePlainPassword() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = service.createUser(new CreateUserRequest("alice", "s3cretpass", Role.MERCHANT));

        assertThat(created.getUsername()).isEqualTo("alice");
        assertThat(created.getRole()).isEqualTo(Role.MERCHANT);
        assertThat(created.getPasswordHash()).isNotEqualTo("s3cretpass");
        assertThat(encoder.matches("s3cretpass", created.getPasswordHash())).isTrue();
    }

    @Test
    void createUser_withATakenUsername_isRejected_andNothingIsSaved() {
        when(userRepository.existsByUsername("admin")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(new CreateUserRequest("admin", "s3cretpass", Role.ADMIN)))
                .isInstanceOf(UsernameAlreadyExistsException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void createUser_whenTheUniqueConstraintWinsTheRace_isReportedAsTaken() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("uq_users_username"));

        assertThatThrownBy(() -> service.createUser(new CreateUserRequest("alice", "s3cretpass", Role.MERCHANT)))
                .isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void createUser_storesAndChecksTheLowerCasedUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = service.createUser(new CreateUserRequest("Alice", "s3cretpass", Role.MERCHANT));

        assertThat(created.getUsername()).isEqualTo("alice");
        verify(userRepository).existsByUsername("alice");
    }
}