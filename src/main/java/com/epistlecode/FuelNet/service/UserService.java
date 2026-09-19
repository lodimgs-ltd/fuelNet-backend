package com.epistlecode.FuelNet.service;

import com.epistlecode.FuelNet.dto.CreateAdminRequest;
import com.epistlecode.FuelNet.dto.UserResponse;
import com.epistlecode.FuelNet.model.User;
import com.epistlecode.FuelNet.model.UserRole;
import com.epistlecode.FuelNet.model.UserStatus;
import com.epistlecode.FuelNet.request.CreateUserRequest;
import com.epistlecode.FuelNet.response.AuthResponse;
import com.epistlecode.FuelNet.request.LoginRequest;

import java.util.List;

public interface UserService {

    // ---- auth ----
    AuthResponse register(CreateUserRequest request);
    AuthResponse login(LoginRequest request);
    User findUserByEmail(String email);
    User requireUserByEmail(String email);

    // ---- admin user management ----
    List<UserResponse> getUsers();
    UserResponse createAdmin(CreateAdminRequest request);
    UserResponse updateRole(Long userId, UserRole role, String actingAdminEmail);
    UserResponse updateStatus(Long userId, UserStatus status, String actingAdminEmail);
}
