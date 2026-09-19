package com.epistlecode.FuelNet.dto;

import com.epistlecode.FuelNet.model.Station;
import com.epistlecode.FuelNet.model.StationStatus;

import java.sql.Timestamp;

public record StationResponse(
        Long id,
        String name,
        String address,
        String city,
        String state,
        Double latitude,
        Double longitude,
        StationStatus status,
        Timestamp createdAt
) {
    public static StationResponse from(Station s) {
        return new StationResponse(s.getId(), s.getName(), s.getAddress(), s.getCity(), s.getState(),
                s.getLatitude(), s.getLongitude(), s.getStatus(), s.getCreatedAt());
    }
}
