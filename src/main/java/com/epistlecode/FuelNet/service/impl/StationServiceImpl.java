package com.epistlecode.FuelNet.service.impl;

import com.epistlecode.FuelNet.dto.CreateStationRequest;
import com.epistlecode.FuelNet.dto.NearbyStationResponse;
import com.epistlecode.FuelNet.model.FuelPrice;
import com.epistlecode.FuelNet.repository.FuelPriceRepository;
import com.epistlecode.FuelNet.util.GeoUtils;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class StationServiceImpl implements StationService {

    private final StationRepository stationRepository;
    private final FuelPriceRepository fuelPriceRepository;

    public StationServiceImpl(StationRepository stationRepository, FuelPriceRepository fuelPriceRepository) {
        this.stationRepository = stationRepository;
        this.fuelPriceRepository = fuelPriceRepository;
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

    @Override
    public List<NearbyStationResponse> findNearby(double lat, double lng, double radiusKm, String fuelType, int limit) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Invalid coordinates");
        }
        double radius = radiusKm <= 0 ? 25 : Math.min(radiusKm, 500);
        int cap = Math.max(1, Math.min(limit, 100));

        // Current price per station, keyed by station id.
        Map<Long, Map<String, Double>> pricesByStation = new HashMap<>();
        for (FuelPrice p : fuelPriceRepository.findCurrentPrices()) {
            pricesByStation.computeIfAbsent(p.getStation().getId(), k -> new HashMap<>())
                    .put(p.getFuelType().getName(), p.getPrice());
        }

        List<NearbyStationResponse> out = new ArrayList<>();
        for (Station s : stationRepository.findByStatusOrderByNameAsc(StationStatus.ACTIVE)) {
            if (s.getLatitude() == null || s.getLongitude() == null) continue;
            double d = GeoUtils.distanceKm(lat, lng, s.getLatitude(), s.getLongitude());
            if (d > radius) continue;
            Map<String, Double> prices = pricesByStation.getOrDefault(s.getId(), Map.of());
            if (fuelType != null && !prices.keySet().stream().anyMatch(k -> k.equalsIgnoreCase(fuelType))) continue;
            out.add(new NearbyStationResponse(s.getId(), s.getName(), s.getAddress(), s.getCity(), s.getState(),
                    s.getLatitude(), s.getLongitude(), GeoUtils.round1(d), prices));
        }

        Comparator<NearbyStationResponse> byDistance = Comparator.comparingDouble(NearbyStationResponse::distanceKm);
        if (fuelType != null) {
            Comparator<NearbyStationResponse> byPrice = Comparator.comparingDouble(r -> priceFor(r.prices(), fuelType));
            out.sort(byPrice.thenComparing(byDistance));
        } else {
            out.sort(byDistance);
        }
        return out.size() > cap ? out.subList(0, cap) : out;
    }

    private static double priceFor(Map<String, Double> prices, String fuelType) {
        return prices.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(fuelType))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(Double.MAX_VALUE);
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
