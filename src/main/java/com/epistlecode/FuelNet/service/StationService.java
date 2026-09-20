package com.epistlecode.FuelNet.service;

import com.epistlecode.FuelNet.dto.CreateStationRequest;
import com.epistlecode.FuelNet.dto.NearbyStationResponse;
import com.epistlecode.FuelNet.dto.StationResponse;
import com.epistlecode.FuelNet.model.StationStatus;

import java.util.List;

public interface StationService {
    List<StationResponse> getStations(boolean includeInactive);
    StationResponse getStation(Long id);
    StationResponse createStation(CreateStationRequest request);
    StationResponse updateStation(Long id, CreateStationRequest request);
    StationResponse updateStatus(Long id, StationStatus status);

    /**
     * Active stations with coordinates within {@code radiusKm} of a point, nearest first,
     * each with its current prices. If {@code fuelType} is given, only stations that
     * currently sell it are returned, sorted by that fuel's price then distance.
     */
    List<NearbyStationResponse> findNearby(double lat, double lng, double radiusKm, String fuelType, int limit);
}
