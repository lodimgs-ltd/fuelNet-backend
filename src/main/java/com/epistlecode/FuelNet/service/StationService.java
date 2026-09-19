package com.epistlecode.FuelNet.service;

import com.epistlecode.FuelNet.dto.CreateStationRequest;
import com.epistlecode.FuelNet.dto.StationResponse;
import com.epistlecode.FuelNet.model.StationStatus;

import java.util.List;

public interface StationService {
    List<StationResponse> getStations(boolean includeInactive);
    StationResponse getStation(Long id);
    StationResponse createStation(CreateStationRequest request);
    StationResponse updateStation(Long id, CreateStationRequest request);
    StationResponse updateStatus(Long id, StationStatus status);
}
