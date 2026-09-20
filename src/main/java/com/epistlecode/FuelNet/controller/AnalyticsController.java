package com.epistlecode.FuelNet.controller;

import com.epistlecode.FuelNet.dto.AnalyticsDtos.AdminActivity;
import com.epistlecode.FuelNet.dto.AnalyticsDtos.DailyChanges;
import com.epistlecode.FuelNet.dto.AnalyticsDtos.FuelSummary;
import com.epistlecode.FuelNet.dto.AnalyticsDtos.Overview;
import com.epistlecode.FuelNet.dto.AnalyticsDtos.StateAverage;
import com.epistlecode.FuelNet.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Aggregations over the price history")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Headline numbers for the dashboard (admin)")
    public ResponseEntity<Overview> overview() {
        return ResponseEntity.ok(analyticsService.getOverview());
    }

    @GetMapping("/fuels")
    @SecurityRequirements
    @Operation(summary = "Per-fuel network average/min/max with week-over-week change")
    public ResponseEntity<List<FuelSummary>> fuels() {
        return ResponseEntity.ok(analyticsService.getFuelSummaries());
    }

    @GetMapping("/by-state")
    @SecurityRequirements
    @Operation(summary = "Average price per state per fuel type")
    public ResponseEntity<List<StateAverage>> byState() {
        return ResponseEntity.ok(analyticsService.getStateAverages());
    }

    @GetMapping("/changes-per-day")
    @SecurityRequirements
    @Operation(summary = "Number of price changes recorded per day (zero-filled)")
    public ResponseEntity<List<DailyChanges>> changesPerDay(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(analyticsService.getChangesPerDay(days));
    }

    @GetMapping("/admin-activity")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Price changes recorded per administrator (admin)")
    public ResponseEntity<List<AdminActivity>> adminActivity() {
        return ResponseEntity.ok(analyticsService.getAdminActivity());
    }
}
