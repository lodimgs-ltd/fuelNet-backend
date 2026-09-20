package com.epistlecode.FuelNet.dto;

import com.epistlecode.FuelNet.model.PriceAlert;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreateAlertRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email")
    private String email;

    @NotBlank(message = "Fuel type is required")
    private String fuelType;

    /** Optional; null = any station. */
    private Long stationId;

    @NotNull(message = "Condition is required (BELOW or ABOVE)")
    private PriceAlert.Condition condition;

    @NotNull(message = "Threshold price is required")
    @Positive(message = "Threshold must be greater than zero")
    private Double threshold;
}
