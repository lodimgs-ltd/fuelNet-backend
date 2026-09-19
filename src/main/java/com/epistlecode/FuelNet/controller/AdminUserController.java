package com.epistlecode.FuelNet.controller;

import com.epistlecode.FuelNet.dto.CreateAdminRequest;
import com.epistlecode.FuelNet.dto.UpdateUserRoleRequest;
import com.epistlecode.FuelNet.dto.UpdateUserStatusRequest;
import com.epistlecode.FuelNet.dto.UserResponse;
import com.epistlecode.FuelNet.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin · Users", description = "User and administrator management (ROLE_ADMIN only)")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List all users")
    public ResponseEntity<List<UserResponse>> list() {
        return ResponseEntity.ok(userService.getUsers());
    }

    @PostMapping
    @Operation(summary = "Create a new administrator account")
    public ResponseEntity<UserResponse> createAdmin(@Valid @RequestBody CreateAdminRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createAdmin(req));
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Change a user's role")
    public ResponseEntity<UserResponse> updateRole(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateUserRoleRequest req,
                                                   Authentication authentication) {
        return ResponseEntity.ok(userService.updateRole(id, req.getRole(), authentication.getName()));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Enable or disable a user")
    public ResponseEntity<UserResponse> updateStatus(@PathVariable Long id,
                                                     @Valid @RequestBody UpdateUserStatusRequest req,
                                                     Authentication authentication) {
        return ResponseEntity.ok(userService.updateStatus(id, req.getStatus(), authentication.getName()));
    }
}
