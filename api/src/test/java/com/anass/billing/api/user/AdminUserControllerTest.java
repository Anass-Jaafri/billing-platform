package com.anass.billing.api.user;

import com.anass.billing.api.config.SecurityConfig;
import com.anass.billing.api.exception.UsernameAlreadyExistsException;
import com.anass.billing.api.security.JwtService;
import com.anass.billing.common.user.Role;
import com.anass.billing.common.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@WithMockUser(roles = "ADMIN")
@Import(SecurityConfig.class)
class AdminUserControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    UserService userService;
    @MockBean
    JwtService jwtService;

    private ResultActions postUser(String json) throws Exception {
        return mockMvc.perform(post("/admin/users").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void create_returns201_andNeverExposesThePasswordOrHash() throws Exception {
        User created = User.builder().username("alice").passwordHash("$2a$secret-hash").role(Role.MERCHANT).build();
        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(created);

        postUser("{\"username\":\"alice\",\"password\":\"s3cretpass\",\"role\":\"MERCHANT\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("MERCHANT"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void create_withATakenUsername_returns409() throws Exception {
        when(userService.createUser(any(CreateUserRequest.class))).thenThrow(new UsernameAlreadyExistsException("admin"));

        postUser("{\"username\":\"admin\",\"password\":\"s3cretpass\",\"role\":\"ADMIN\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username already exists: admin"));
    }

    @Test
    void create_withAShortPassword_returns400WithFieldError() throws Exception {
        postUser("{\"username\":\"alice\",\"password\":\"short\",\"role\":\"MERCHANT\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
        verifyNoInteractions(userService);
    }

    @Test
    void create_withBadUsernameCharacters_returns400() throws Exception {
        postUser("{\"username\":\"al ice\",\"password\":\"s3cretpass\",\"role\":\"MERCHANT\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("username"));
    }

    @Test
    void create_withAMissingRole_returns400() throws Exception {
        postUser("{\"username\":\"alice\",\"password\":\"s3cretpass\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("role"));
    }

    @Test
    void create_withAnUnknownRole_returns400NamingTheField() throws Exception {
        postUser("{\"username\":\"alice\",\"password\":\"s3cretpass\",\"role\":\"ROOT\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for field 'role'"));
        verifyNoInteractions(userService);
    }

    @Test
    void create_withMalformedJson_returns400() throws Exception {
        postUser("{ this is not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
    }

    @Test
    @WithMockUser(roles = "MERCHANT")
    void create_asMerchant_returns403_andNothingIsCreated() throws Exception {
        postUser("{\"username\":\"alice\",\"password\":\"s3cretpass\",\"role\":\"MERCHANT\"}")
                .andExpect(status().isForbidden());
        verifyNoInteractions(userService);
    }
}
