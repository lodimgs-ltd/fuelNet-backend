package com.epistlecode.FuelNet.controller;

import com.epistlecode.FuelNet.config.JwtProvider;
import com.epistlecode.FuelNet.config.SecurityConfig;
import com.epistlecode.FuelNet.dto.UserResponse;
import com.epistlecode.FuelNet.exception.GlobalExceptionHandler;
import com.epistlecode.FuelNet.model.UserRole;
import com.epistlecode.FuelNet.model.UserStatus;
import com.epistlecode.FuelNet.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import({SecurityConfig.class, JwtProvider.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-key-for-fuelnet-unit-tests-32-bytes-min",
        "app.cors.allowed-origins=http://localhost:5173"
})
class AdminUserControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean UserService userService;

    private String tokenFor(String email, String role) {
        return "Bearer " + jwtProvider.generateToken(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority(role))));
    }

    @Test
    void listUsersIsAdminOnly() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").header("Authorization", tokenFor("jane@example.com", "ROLE_USER")))
                .andExpect(status().isForbidden());

        when(userService.getUsers()).thenReturn(List.of(
                new UserResponse(1L, "System Admin", "admin@fuelnet.com", UserRole.ROLE_ADMIN, UserStatus.ENABLED, null, null)));
        mvc.perform(get("/api/admin/users").header("Authorization", tokenFor("admin@fuelnet.com", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("admin@fuelnet.com"));
    }

    @Test
    void createAdminValidatesPasswordLength() throws Exception {
        mvc.perform(post("/api/admin/users")
                        .header("Authorization", tokenFor("admin@fuelnet.com", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"New Admin","email":"new@fuelnet.com","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        verifyNoInteractions(userService);
    }

    @Test
    void createAdminHappyPath() throws Exception {
        when(userService.createAdmin(any())).thenReturn(
                new UserResponse(2L, "New Admin", "new@fuelnet.com", UserRole.ROLE_ADMIN, UserStatus.ENABLED, null, null));

        mvc.perform(post("/api/admin/users")
                        .header("Authorization", tokenFor("admin@fuelnet.com", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"New Admin","email":"new@fuelnet.com","password":"longenough"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));
    }

    @Test
    void statusChangePassesCallerEmailToService() throws Exception {
        when(userService.updateStatus(eq(7L), eq(UserStatus.DISABLED), eq("admin@fuelnet.com"))).thenReturn(
                new UserResponse(7L, "Jane", "jane@example.com", UserRole.ROLE_USER, UserStatus.DISABLED, null, null));

        mvc.perform(patch("/api/admin/users/7/status")
                        .header("Authorization", tokenFor("admin@fuelnet.com", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    void selfDemotionErrorMapsTo400() throws Exception {
        when(userService.updateRole(eq(1L), eq(UserRole.ROLE_USER), eq("admin@fuelnet.com")))
                .thenThrow(new IllegalArgumentException("You cannot remove your own admin role"));

        mvc.perform(patch("/api/admin/users/1/role")
                        .header("Authorization", tokenFor("admin@fuelnet.com", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ROLE_USER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot remove your own admin role"));
    }
}
