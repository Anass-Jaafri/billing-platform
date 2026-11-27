package com.anass.billing.api.user;

import com.anass.billing.api.exception.UsernameAlreadyExistsException;
import com.anass.billing.common.user.User;
import com.anass.billing.common.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User createUser(CreateUserRequest request) {
        String username = Usernames.normalize(request.username());
        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException(username);
        }
        User user = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // lost a race with another request: the unique constraint is the final authority
            throw new UsernameAlreadyExistsException(username);
        }
    }
}