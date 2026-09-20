package com.epistlecode.FuelNet.controller;

import com.epistlecode.FuelNet.dto.CreateStationRequest;
import com.epistlecode.FuelNet.dto.NearbyStationResponse;
import com.epistlecode.FuelNet.dto.StationResponse;
import com.epistlecode.FuelNet.model.StationStatus;
import com.epistlecode.FuelNet.service.StationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stations")
@Tag(name = "Stations", description = "Filling stations in the FuelNet network")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "List stations (active only unless includeInactive=true)")
    public ResponseEntity<List<StationResponse>> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(stationService.getStations(includeInactive));
    }

    @GetMapping("/nearby")
    @SecurityRequirements
    @Operation(summary = "Active stations near a point with current prices, nearest first "
            + "(or cheapest first when fuelType is given)")
    public ResponseEntity<List<NearbyStationResponse>> nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "25") double radiusKm,
            @RequestParam(required = false) String fuelType,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(stationService.findNearby(lat, lng, radiusKm, fuelType, limit));
    }

    @GetMapping("/{id}")
    @SecurityRequirements
    @Operation(summary = "Get one station")
    public ResponseEntity<StationResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(stationService.getStation(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a station (admin)")
    public ResponseEntity<StationResponse> create(@Valid @RequestBody CreateStationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stationService.createStation(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a station's details (admin)")
    public ResponseEntity<StationResponse> update(@PathVariable Long id, @Valid @RequestBody CreateStationRequest req) {
        return ResponseEntity.ok(stationService.updateStation(id, req));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate or deactivate a station (admin)")
    public ResponseEntity<StationResponse> updateStatus(@PathVariable Long id,
                                                        @RequestBody Map<String, StationStatus> body) {
        StationStatus status = body.get("status");
        if (status == null) {
            throw new IllegalArgumentException("status is required (ACTIVE or INACTIVE)");
        }
        return ResponseEntity.ok(stationService.updateStatus(id, status));
    }
}
