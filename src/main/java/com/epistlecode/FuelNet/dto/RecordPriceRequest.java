package com.epistlecode.FuelNet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class RecordPriceRequest {
    @NotNull(message = "Station is required")
    private Long stationId;

    @NotBlank(message = "Fuel type is required")
    private String fuelType;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be greater than zero")
    private Double price;
}
