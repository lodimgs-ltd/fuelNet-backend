package com.epistlecode.FuelNet.controller;

import com.epistlecode.FuelNet.dto.AlertNotificationResponse;
import com.epistlecode.FuelNet.dto.AlertResponse;
import com.epistlecode.FuelNet.dto.PageResponse;
import com.epistlecode.FuelNet.service.PriceAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/alerts")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin · Alerts", description = "Manage subscriptions and inspect the delivery log")
public class AdminAlertController {

    private final PriceAlertService alertService;

    public AdminAlertController(PriceAlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    @Operation(summary = "List all alert subscriptions")
    public ResponseEntity<List<AlertResponse>> list() {
        return ResponseEntity.ok(alertService.getAlerts());
    }

    @PatchMapping("/{id}/active")
    @Operation(summary = "Activate or deactivate a subscription")
    public ResponseEntity<AlertResponse> setActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean active = body.get("active");
        if (active == null) throw new IllegalArgumentException("active is required");
        return ResponseEntity.ok(alertService.setActive(id, active));
    }

    @GetMapping("/notifications")
    @Operation(summary = "Paginated log of notifications sent")
    public ResponseEntity<PageResponse<AlertNotificationResponse>> notifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(alertService.getNotifications(page, size));
    }

    @PostMapping("/sweep")
    @Operation(summary = "Run the alert evaluation sweep now")
    public ResponseEntity<Map<String, Object>> sweep() {
        int sent = alertService.sweep();
        return ResponseEntity.ok(Map.of("notificationsSent", sent, "channel", alertService.channel()));
    }

    @GetMapping("/channel")
    @Operation(summary = "Which delivery channel is configured (email or log)")
    public ResponseEntity<Map<String, String>> channel() {
        return ResponseEntity.ok(Map.of("channel", alertService.channel()));
    }
}
