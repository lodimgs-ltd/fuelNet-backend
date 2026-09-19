package com.epistlecode.FuelNet.service.impl;

import com.epistlecode.FuelNet.config.JwtProvider;
import com.epistlecode.FuelNet.dto.CreateAdminRequest;
import com.epistlecode.FuelNet.dto.UserResponse;
import com.epistlecode.FuelNet.exception.ConflictException;
import com.epistlecode.FuelNet.exception.ResourceNotFoundException;
import com.epistlecode.FuelNet.model.User;
import com.epistlecode.FuelNet.model.UserRole;
import com.epistlecode.FuelNet.model.UserStatus;
import com.epistlecode.FuelNet.repository.UserRepository;
import com.epistlecode.FuelNet.request.CreateUserRequest;
import com.epistlecode.FuelNet.request.LoginRequest;
import com.epistlecode.FuelNet.response.AuthResponse;
import com.epistlecode.FuelNet.service.UserService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserServicesImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public UserServicesImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    // ------------------------------------------------------------------ auth

    @Override
    @Transactional
    public AuthResponse register(CreateUserRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already in use");
        }
        User user = newUser(req.getFullName(), email, req.getPassword(), UserRole.ROLE_USER);
        userRepository.save(user);
        return buildAuthResponse(user, "Registration successful");
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email);
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (user.getStatus() != UserStatus.ENABLED) {
            throw new DisabledException("Account is disabled");
        }
        user.setLastLogin(new Timestamp(System.currentTimeMillis()));
        userRepository.save(user);
        return buildAuthResponse(user, "Login successful");
    }

    @Override
    public User findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public User requireUserByEmail(String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found: " + email);
        }
        return user;
    }

    // ------------------------------------------------------- admin management

    @Override
    public List<UserResponse> getUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc().stream().map(UserResponse::from).toList();
    }

    @Override
    @Transactional
    public UserResponse createAdmin(CreateAdminRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already in use");
        }
        User admin = newUser(req.getFullName(), email, req.getPassword(), UserRole.ROLE_ADMIN);
        return UserResponse.from(userRepository.save(admin));
    }

    @Override
    @Transactional
    public UserResponse updateRole(Long userId, UserRole role, String actingAdminEmail) {
        User user = requireUser(userId);
        if (user.getEmail().equalsIgnoreCase(actingAdminEmail) && role != UserRole.ROLE_ADMIN) {
            throw new IllegalArgumentException("You cannot remove your own admin role");
        }
        user.setRole(role);
        return UserResponse.from(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse updateStatus(Long userId, UserStatus status, String actingAdminEmail) {
        User user = requireUser(userId);
        if (user.getEmail().equalsIgnoreCase(actingAdminEmail) && status == UserStatus.DISABLED) {
            throw new IllegalArgumentException("You cannot disable your own account");
        }
        user.setStatus(status);
        return UserResponse.from(userRepository.save(user));
    }

    // --------------------------------------------------------------- helpers

    private User newUser(String fullName, String email, String rawPassword, UserRole role) {
        User user = new User();
        user.setFullName(fullName.trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setStatus(UserStatus.ENABLED);
        user.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        return user;
    }

    private AuthResponse buildAuthResponse(User user, String message) {
        UserRole role = user.getRole() == null ? UserRole.ROLE_USER : user.getRole();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, List.of(new SimpleGrantedAuthority(role.name())));
        AuthResponse response = new AuthResponse();
        response.setJwt(jwtProvider.generateToken(authentication));
        response.setMessage(message);
        response.setRole(role);
        return response;
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }
}
