package com.epistlecode.FuelNet.dto;

import java.util.Map;

/** A station with its distance from the caller and its current price per fuel type. */
public record NearbyStationResponse(
        Long id,
        String name,
        String address,
        String city,
        String state,
        double latitude,
        double longitude,
        double distanceKm,
        /** fuelType → current price; fuels with no price yet are absent */
        Map<String, Double> prices
) {}
