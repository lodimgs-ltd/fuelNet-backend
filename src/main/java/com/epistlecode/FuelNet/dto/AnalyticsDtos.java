package com.epistlecode.FuelNet.dto;

import java.sql.Timestamp;
import java.util.List;

/** Response records for the analytics endpoints. */
public final class AnalyticsDtos {

    private AnalyticsDtos() {}

    public record FuelSummary(
            String fuelType,
            int stationCount,
            double average,
            double min,
            String minStation,
            double max,
            String maxStation,
            /** Network average 7 days ago, or null if no data existed then. */
            Double averageWeekAgo,
            Double weekChange,
            Double weekChangePercent,
            long changesLast7Days
    ) {}

    public record StateAverage(String state, String fuelType, int stationCount, double average, double min, double max) {}

    public record DailyChanges(String date, long changes) {}

    public record AdminActivity(Long userId, String fullName, String email, long changes, Timestamp lastChangeAt) {}

    public record Overview(
            long totalStations,
            long activeStations,
            long fuelTypes,
            long priceRecords,
            long changesLast7Days,
            long changesLast30Days,
            long activeAlerts,
            long notificationsSent,
            List<FuelSummary> fuels
    ) {}
}
