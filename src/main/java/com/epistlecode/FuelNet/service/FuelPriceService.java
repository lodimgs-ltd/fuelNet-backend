package com.epistlecode.FuelNet.service;

import com.epistlecode.FuelNet.dto.FuelPriceResponse;
import com.epistlecode.FuelNet.dto.PageResponse;
import com.epistlecode.FuelNet.dto.RecordPriceRequest;
import com.epistlecode.FuelNet.model.User;

import java.util.List;

public interface FuelPriceService {

    /** Latest price for every (station, fuel type) pair. */
    List<FuelPriceResponse> getCurrentPrices();

    /** Latest price for every fuel type at one station. */
    List<FuelPriceResponse> getCurrentPricesForStation(Long stationId);

    /** Append a new price record. Never overwrites. */
    FuelPriceResponse recordPrice(RecordPriceRequest request, User setBy);

    /** Time series for one station + fuel type, newest first. */
    List<FuelPriceResponse> getHistory(Long stationId, String fuelType, int limit);

    /** Network-wide audit log, newest first. Optionally filtered by station. */
    PageResponse<FuelPriceResponse> getAuditLog(Long stationId, int page, int size);

    List<String> getFuelTypeNames();
}
