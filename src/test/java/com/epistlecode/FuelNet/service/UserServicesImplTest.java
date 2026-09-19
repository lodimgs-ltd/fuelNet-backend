package com.epistlecode.FuelNet.service;

import com.epistlecode.FuelNet.config.JwtProvider;
import com.epistlecode.FuelNet.dto.UserResponse;
import com.epistlecode.FuelNet.exception.ConflictException;
import com.epistlecode.FuelNet.model.User;
import com.epistlecode.FuelNet.model.UserRole;
import com.epistlecode.FuelNet.model.UserStatus;
import com.epistlecode.FuelNet.repository.UserRepository;
import com.epistlecode.FuelNet.request.CreateUserRequest;
import com.epistlecode.FuelNet.request.LoginRequest;
import com.epistlecode.FuelNet.response.AuthResponse;
import com.epistlecode.FuelNet.service.impl.UserServicesImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServicesImplTest {

    @Mock UserRepository userRepository;

    PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    JwtProvider jwtProvider = new JwtProvider("test-secret-key-for-fuelnet-unit-tests-32-bytes-min", 60_000);
    UserServicesImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServicesImpl(userRepository, encoder, jwtProvider);
    }

    private User enabledUser(String email, String rawPassword, UserRole role) {
        User u = new User();
        u.setId(5L);
        u.setFullName("Jane Doe");
        u.setEmail(email);
        u.setPassword(encoder.encode(rawPassword));
        u.setRole(role);
        u.setStatus(UserStatus.ENABLED);
        return u;
    }

    @Test
    void registerLowercasesEmailHashesPasswordAndIssuesUserToken() {
        CreateUserRequest req = new CreateUserRequest();
        req.setFullName(" Jane Doe ");
        req.setEmail("Jane@Example.com");
        req.setPassword("password123");
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = service.register(req);

        verify(userRepository).save(argThat(u ->
                u.getEmail().equals("jane@example.com")
                        && u.getFullName().equals("Jane Doe")
                        && !u.getPassword().equals("password123")
                        && encoder.matches("password123", u.getPassword())
                        && u.getRole() == UserRole.ROLE_USER
                        && u.getStatus() == UserStatus.ENABLED));
        assertThat(response.getRole()).isEqualTo(UserRole.ROLE_USER);
        assertThat(jwtProvider.getEmailFromJwtToken(response.getJwt())).isEqualTo("jane@example.com");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        CreateUserRequest req = new CreateUserRequest();
        req.setFullName("Jane");
        req.setEmail("jane@example.com");
        req.setPassword("password123");
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(req)).isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithCorrectPasswordReturnsTokenCarryingRole() {
        when(userRepository.findByEmail("admin@fuelnet.com"))
                .thenReturn(enabledUser("admin@fuelnet.com", "admin123", UserRole.ROLE_ADMIN));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginRequest req = new LoginRequest();
        req.setEmail("admin@fuelnet.com");
        req.setPassword("admin123");
        AuthResponse response = service.login(req);

        assertThat(response.getRole()).isEqualTo(UserRole.ROLE_ADMIN);
        assertThat(jwtProvider.parseClaims(response.getJwt()).get("authorities")).isEqualTo("ROLE_ADMIN");
        verify(userRepository).save(argThat(u -> u.getLastLogin() != null));
    }

    @Test
    void loginWithWrongPasswordThrowsBadCredentials() {
        when(userRepository.findByEmail("admin@fuelnet.com"))
                .thenReturn(enabledUser("admin@fuelnet.com", "admin123", UserRole.ROLE_ADMIN));

        LoginRequest req = new LoginRequest();
        req.setEmail("admin@fuelnet.com");
        req.setPassword("wrong");
        assertThatThrownBy(() -> service.login(req)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginWithUnknownEmailThrowsSameErrorAsWrongPassword() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(null);

        LoginRequest req = new LoginRequest();
        req.setEmail("ghost@example.com");
        req.setPassword("whatever");
        assertThatThrownBy(() -> service.login(req)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginOnDisabledAccountThrowsDisabled() {
        User u = enabledUser("jane@example.com", "password123", UserRole.ROLE_USER);
        u.setStatus(UserStatus.DISABLED);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(u);

        LoginRequest req = new LoginRequest();
        req.setEmail("jane@example.com");
        req.setPassword("password123");
        assertThatThrownBy(() -> service.login(req)).isInstanceOf(DisabledException.class);
    }

    @Test
    void adminCannotDemoteThemselves() {
        User me = enabledUser("admin@fuelnet.com", "admin123", UserRole.ROLE_ADMIN);
        when(userRepository.findById(5L)).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> service.updateRole(5L, UserRole.ROLE_USER, "admin@fuelnet.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adminCannotDisableThemselves() {
        User me = enabledUser("admin@fuelnet.com", "admin123", UserRole.ROLE_ADMIN);
        when(userRepository.findById(5L)).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> service.updateStatus(5L, UserStatus.DISABLED, "admin@fuelnet.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adminCanPromoteAnotherUser() {
        User other = enabledUser("jane@example.com", "password123", UserRole.ROLE_USER);
        when(userRepository.findById(5L)).thenReturn(Optional.of(other));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse r = service.updateRole(5L, UserRole.ROLE_ADMIN, "admin@fuelnet.com");
        assertThat(r.role()).isEqualTo(UserRole.ROLE_ADMIN);
    }
}
