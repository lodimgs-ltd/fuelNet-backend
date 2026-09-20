package com.epistlecode.FuelNet.controller;

import com.epistlecode.FuelNet.dto.AlertResponse;
import com.epistlecode.FuelNet.dto.CreateAlertRequest;
import com.epistlecode.FuelNet.service.PriceAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alerts")
@Tag(name = "Price Alerts", description = "Public price-alert subscriptions")
public class PriceAlertController {

    private final PriceAlertService alertService;

    public PriceAlertController(PriceAlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping
    @SecurityRequirements
    @Operation(summary = "Subscribe to be notified when a fuel price crosses a threshold")
    public ResponseEntity<AlertResponse> subscribe(@Valid @RequestBody CreateAlertRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alertService.subscribe(req));
    }

    @PostMapping("/unsubscribe/{token}")
    @SecurityRequirements
    @Operation(summary = "Deactivate an alert using the token from the notification email")
    public ResponseEntity<AlertResponse> unsubscribe(@PathVariable String token) {
        return ResponseEntity.ok(alertService.unsubscribe(token));
    }
}
