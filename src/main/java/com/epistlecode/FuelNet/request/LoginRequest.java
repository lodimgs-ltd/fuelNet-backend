package com.epistlecode.FuelNet.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Email is required to login")
    @Email(message = "please provide a valid mail")
    private String email;

    @NotBlank(message = "password is required to login")
    private String password;
}
