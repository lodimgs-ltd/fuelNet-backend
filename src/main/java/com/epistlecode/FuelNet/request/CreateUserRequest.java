package com.epistlecode.FuelNet.request;

import com.epistlecode.FuelNet.annotations.UniqueEmail;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank(message = "Your name is required")
    private String fullName;

    @NotBlank(message = "email is required")
    @Email(message = "please provide a valid mail")
    @UniqueEmail
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

}
