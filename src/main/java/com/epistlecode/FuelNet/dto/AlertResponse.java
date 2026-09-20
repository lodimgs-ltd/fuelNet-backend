package com.epistlecode.FuelNet.dto;

import com.epistlecode.FuelNet.model.PriceAlert;

import java.sql.Timestamp;

public record AlertResponse(
        Long id,
        String email,
        String fuelType,
        Long stationId,
        String stationName,
        PriceAlert.Condition condition,
        double threshold,
        boolean active,
        boolean triggered,
        Timestamp lastNotifiedAt,
        Timestamp createdAt
) {
    public static AlertResponse from(PriceAlert a) {
        return new AlertResponse(a.getId(), a.getEmail(), a.getFuelType().getName(),
                a.getStation() != null ? a.getStation().getId() : null,
                a.getStation() != null ? a.getStation().getName() : null,
                a.getCondition(), a.getThreshold(), a.isActive(), a.isTriggered(),
                a.getLastNotifiedAt(), a.getCreatedAt());
    }
}
