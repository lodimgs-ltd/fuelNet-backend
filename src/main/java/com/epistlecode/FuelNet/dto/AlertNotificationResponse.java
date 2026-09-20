package com.epistlecode.FuelNet.dto;

import com.epistlecode.FuelNet.model.AlertNotification;

import java.sql.Timestamp;

public record AlertNotificationResponse(
        Long id,
        Long alertId,
        String email,
        String fuelType,
        String stationName,
        double price,
        String message,
        AlertNotification.Status status,
        String error,
        Timestamp createdAt
) {
    public static AlertNotificationResponse from(AlertNotification n) {
        return new AlertNotificationResponse(n.getId(), n.getAlert().getId(), n.getEmail(),
                n.getFuelPrice().getFuelType().getName(),
                n.getFuelPrice().getStation() != null ? n.getFuelPrice().getStation().getName() : null,
                n.getFuelPrice().getPrice(), n.getMessage(), n.getStatus(), n.getError(), n.getCreatedAt());
    }
}
