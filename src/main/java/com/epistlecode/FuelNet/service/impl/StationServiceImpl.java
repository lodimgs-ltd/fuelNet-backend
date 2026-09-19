package com.epistlecode.FuelNet.service.impl;

import com.epistlecode.FuelNet.dto.CreateStationRequest;
import com.epistlecode.FuelNet.dto.StationResponse;
import com.epistlecode.FuelNet.exception.ConflictException;
import com.epistlecode.FuelNet.exception.ResourceNotFoundException;
import com.epistlecode.FuelNet.model.Station;
import com.epistlecode.FuelNet.model.StationStatus;
import com.epistlecode.FuelNet.repository.StationRepository;
import com.epistlecode.FuelNet.service.StationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class StationServiceImpl implements StationService {

    private final StationRepository stationRepository;

    public StationServiceImpl(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    @Override
    public List<StationResponse> getStations(boolean includeInactive) {
        List<Station> stations = includeInactive
                ? stationRepository.findAllByOrderByNameAsc()
                : stationRepository.findByStatusOrderByNameAsc(StationStatus.ACTIVE);
        return stations.stream().map(StationResponse::from).toList();
    }

    @Override
    public StationResponse getStation(Long id) {
        return StationResponse.from(require(id));
    }

    @Override
    @Transactional
    public StationResponse createStation(CreateStationRequest req) {
        if (stationRepository.existsByNameIgnoreCaseAndAddressIgnoreCase(req.getName(), req.getAddress())) {
            throw new ConflictException("A station with this name and address already exists");
        }
        Station station = new Station();
        apply(station, req);
        station.setStatus(StationStatus.ACTIVE);
        station.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        return StationResponse.from(stationRepository.save(station));
    }

    @Override
    @Transactional
    public StationResponse updateStation(Long id, CreateStationRequest req) {
        Station station = require(id);
        apply(station, req);
        return StationResponse.from(stationRepository.save(station));
    }

    @Override
    @Transactional
    public StationResponse updateStatus(Long id, StationStatus status) {
        Station station = require(id);
        station.setStatus(status);
        return StationResponse.from(stationRepository.save(station));
    }

    private void apply(Station station, CreateStationRequest req) {
        station.setName(req.getName().trim());
        station.setAddress(req.getAddress().trim());
        station.setCity(req.getCity());
        station.setState(req.getState());
        station.setLatitude(req.getLatitude());
        station.setLongitude(req.getLongitude());
    }

    private Station require(Long id) {
        return stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found: " + id));
    }
}
