package com.epistlecode.FuelNet.controller;

import com.epistlecode.FuelNet.dto.UserResponse;
import com.epistlecode.FuelNet.request.CreateUserRequest;
import com.epistlecode.FuelNet.request.LoginRequest;
import com.epistlecode.FuelNet.response.AuthResponse;
import com.epistlecode.FuelNet.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@Tag(name = "Auth", description = "Registration, login and the caller's own profile")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @SecurityRequirements
    @Operation(summary = "Register a new (non-admin) user and return a JWT")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody CreateUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(req));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Log in and return a JWT")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(userService.login(req));
    }

    @GetMapping
    @Operation(summary = "Get the profile of the authenticated caller")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        return ResponseEntity.ok(UserResponse.from(userService.requireUserByEmail(authentication.getName())));
    }
}
