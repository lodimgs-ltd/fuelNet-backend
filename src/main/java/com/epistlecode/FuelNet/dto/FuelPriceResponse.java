package com.epistlecode.FuelNet.dto;

import com.epistlecode.FuelNet.model.FuelPrice;

import java.sql.Timestamp;

/** One price record — used both for the current price board and for history/audit rows. */
public record FuelPriceResponse(
        Long id,
        Long stationId,
        String stationName,
        String fuelType,
        double price,
        Double previousPrice,
        Double change,
        Double changePercent,
        String setByName,
        String setByEmail,
        Timestamp createdAt
) {
    public static FuelPriceResponse from(FuelPrice p) {
        Double change = null;
        Double changePercent = null;
        if (p.getPreviousPrice() != null) {
            change = round2(p.getPrice() - p.getPreviousPrice());
            if (p.getPreviousPrice() != 0) {
                changePercent = round2(change / p.getPreviousPrice() * 100);
            }
        }
        return new FuelPriceResponse(
                p.getId(),
                p.getStation() != null ? p.getStation().getId() : null,
                p.getStation() != null ? p.getStation().getName() : null,
                p.getFuelType().getName(),
                p.getPrice(),
                p.getPreviousPrice(),
                change,
                changePercent,
                p.getSetBy() != null ? p.getSetBy().getFullName() : null,
                p.getSetBy() != null ? p.getSetBy().getEmail() : null,
                p.getCreatedAt()
        );
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
