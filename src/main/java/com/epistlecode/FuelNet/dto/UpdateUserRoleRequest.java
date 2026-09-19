package com.epistlecode.FuelNet.dto;

import com.epistlecode.FuelNet.model.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserRoleRequest {
    @NotNull(message = "Role is required")
    private UserRole role;
}
