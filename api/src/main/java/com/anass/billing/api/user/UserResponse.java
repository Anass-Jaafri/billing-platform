package com.anass.billing.api.user;

import com.anass.billing.common.user.Role;
import com.anass.billing.common.user.User;

import java.util.UUID;

public record UserResponse(UUID id, String username, Role role) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole());
    }
}