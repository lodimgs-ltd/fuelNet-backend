package com.epistlecode.FuelNet.service.impl;

import com.epistlecode.FuelNet.dto.FuelPriceResponse;
import com.epistlecode.FuelNet.dto.PageResponse;
import com.epistlecode.FuelNet.dto.RecordPriceRequest;
import com.epistlecode.FuelNet.exception.ResourceNotFoundException;
import com.epistlecode.FuelNet.model.FuelPrice;
import com.epistlecode.FuelNet.model.FuelType;
import com.epistlecode.FuelNet.model.Station;
import com.epistlecode.FuelNet.model.User;
import com.epistlecode.FuelNet.repository.FuelPriceRepository;
import com.epistlecode.FuelNet.repository.FuelTypeRepository;
import com.epistlecode.FuelNet.repository.StationRepository;
import com.epistlecode.FuelNet.service.FuelPriceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class FuelPriceServiceImpl implements FuelPriceService {

    private static final int MAX_HISTORY = 500;

    private final FuelPriceRepository fuelPriceRepository;
    private final FuelTypeRepository fuelTypeRepository;
    private final StationRepository stationRepository;

    public FuelPriceServiceImpl(FuelPriceRepository fuelPriceRepository,
                                FuelTypeRepository fuelTypeRepository,
                                StationRepository stationRepository) {
        this.fuelPriceRepository = fuelPriceRepository;
        this.fuelTypeRepository = fuelTypeRepository;
        this.stationRepository = stationRepository;
    }

    @Override
    public List<FuelPriceResponse> getCurrentPrices() {
        return fuelPriceRepository.findCurrentPrices().stream().map(FuelPriceResponse::from).toList();
    }

    @Override
    public List<FuelPriceResponse> getCurrentPricesForStation(Long stationId) {
        requireStation(stationId);
        return fuelPriceRepository.findCurrentPricesByStation(stationId).stream()
                .map(FuelPriceResponse::from).toList();
    }

    @Override
    @Transactional
    public FuelPriceResponse recordPrice(RecordPriceRequest request, User setBy) {
        Station station = requireStation(request.getStationId());
        FuelType fuelType = requireFuelType(request.getFuelType());

        Double previous = fuelPriceRepository
                .findFirstByStationAndFuelTypeOrderByCreatedAtDesc(station, fuelType)
                .map(FuelPrice::getPrice)
                .orElse(null);

        FuelPrice record = new FuelPrice();
        record.setStation(station);
        record.setFuelType(fuelType);
        record.setPrice(request.getPrice());
        record.setPreviousPrice(previous);
        record.setSetBy(setBy);
        record.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        return FuelPriceResponse.from(fuelPriceRepository.save(record));
    }

    @Override
    public List<FuelPriceResponse> getHistory(Long stationId, String fuelType, int limit) {
        int capped = Math.max(1, Math.min(limit, MAX_HISTORY));
        Pageable pageable = PageRequest.of(0, capped);

        List<FuelPrice> rows;
        if (stationId != null && fuelType != null) {
            rows = fuelPriceRepository.findByStationAndFuelTypeOrderByCreatedAtDesc(
                    requireStation(stationId), requireFuelType(fuelType), pageable);
        } else if (stationId != null) {
            rows = fuelPriceRepository.findByStationOrderByCreatedAtDesc(requireStation(stationId), pageable).getContent();
        } else if (fuelType != null) {
            rows = fuelPriceRepository.findByFuelTypeOrderByCreatedAtDesc(requireFuelType(fuelType), pageable);
        } else {
            rows = fuelPriceRepository.findAllByOrderByCreatedAtDesc(pageable).getContent();
        }
        return rows.stream().map(FuelPriceResponse::from).toList();
    }

    @Override
    public PageResponse<FuelPriceResponse> getAuditLog(Long stationId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
        Page<FuelPrice> result;
        if (stationId == null) {
            result = fuelPriceRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            result = fuelPriceRepository.findByStationOrderByCreatedAtDesc(requireStation(stationId), pageable);
        }
        return PageResponse.of(result.map(FuelPriceResponse::from));
    }

    @Override
    public List<String> getFuelTypeNames() {
        return fuelTypeRepository.findAll().stream().map(FuelType::getName).sorted().toList();
    }

    private Station requireStation(Long id) {
        return stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found: " + id));
    }

    private FuelType requireFuelType(String name) {
        return fuelTypeRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown fuel type: " + name));
    }
}
