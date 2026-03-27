package com.epistlecode.FuelNet.response;

import com.epistlecode.FuelNet.model.UserRole;
import lombok.Data;

@Data
public class AuthResponse {
    private String message;
    private String jwt;
    private UserRole role;
}
