package com.epistlecode.FuelNet.dto;

import com.epistlecode.FuelNet.model.User;
import com.epistlecode.FuelNet.model.UserRole;
import com.epistlecode.FuelNet.model.UserStatus;

import java.sql.Timestamp;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        UserRole role,
        UserStatus status,
        Timestamp lastLogin,
        Timestamp createdAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.getStatus(),
                u.getLastLogin(), u.getCreatedAt());
    }
}
