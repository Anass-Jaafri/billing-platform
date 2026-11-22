package com.anass.billing.api.user;

import com.anass.billing.common.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "may contain only letters, digits, '.', '_' and '-'")
        String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull Role role) {

    
    @Override
    public String toString() {
        return "CreateUserRequest[username=" + username + ", role=" + role + "]";
    }
}
