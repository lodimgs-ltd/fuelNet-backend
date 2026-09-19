package com.epistlecode.FuelNet.controller;

import com.epistlecode.FuelNet.dto.FuelPriceResponse;
import com.epistlecode.FuelNet.dto.PageResponse;
import com.epistlecode.FuelNet.dto.RecordPriceRequest;
import com.epistlecode.FuelNet.model.User;
import com.epistlecode.FuelNet.service.FuelPriceService;
import com.epistlecode.FuelNet.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fuelPrice")
@Tag(name = "Fuel Prices", description = "Current price board, price history and the audit log")
public class FuelPriceController {

    private final FuelPriceService fuelPriceService;
    private final UserService userService;

    public FuelPriceController(FuelPriceService fuelPriceService, UserService userService) {
        this.fuelPriceService = fuelPriceService;
        this.userService = userService;
    }

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Current price for every station + fuel type (optionally one station)")
    public ResponseEntity<List<FuelPriceResponse>> getCurrentPrices(
            @RequestParam(required = false) Long stationId) {
        List<FuelPriceResponse> prices = stationId == null
                ? fuelPriceService.getCurrentPrices()
                : fuelPriceService.getCurrentPricesForStation(stationId);
        return ResponseEntity.ok(prices);
    }

    @GetMapping("/types")
    @SecurityRequirements
    @Operation(summary = "List the fuel types the platform tracks")
    public ResponseEntity<List<String>> getFuelTypes() {
        return ResponseEntity.ok(fuelPriceService.getFuelTypeNames());
    }

    @GetMapping("/history")
    @SecurityRequirements
    @Operation(summary = "Price history (newest first). Filter by stationId and/or fuelType.")
    public ResponseEntity<List<FuelPriceResponse>> getHistory(
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) String fuelType,
            @Parameter(description = "Max rows, capped at 500") @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(fuelPriceService.getHistory(stationId, fuelType, limit));
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Paginated audit log of every price change (admin)")
    public ResponseEntity<PageResponse<FuelPriceResponse>> getAuditLog(
            @RequestParam(required = false) Long stationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(fuelPriceService.getAuditLog(stationId, page, size));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Record a new price for a station + fuel type (admin). Appends to history.")
    public ResponseEntity<FuelPriceResponse> recordPrice(@Valid @RequestBody RecordPriceRequest req,
                                                         Authentication authentication) {
        User admin = userService.requireUserByEmail(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(fuelPriceService.recordPrice(req, admin));
    }
}
