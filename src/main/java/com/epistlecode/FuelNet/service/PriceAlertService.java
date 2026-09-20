package com.epistlecode.FuelNet.service;

import com.epistlecode.FuelNet.dto.AlertNotificationResponse;
import com.epistlecode.FuelNet.dto.AlertResponse;
import com.epistlecode.FuelNet.dto.CreateAlertRequest;
import com.epistlecode.FuelNet.dto.PageResponse;
import com.epistlecode.FuelNet.model.FuelPrice;

import java.util.List;

public interface PriceAlertService {

    AlertResponse subscribe(CreateAlertRequest request);

    /** Deactivates the alert identified by its unsubscribe token. */
    AlertResponse unsubscribe(String token);

    /** Evaluate all active alerts against one newly recorded price. Returns the number of notifications sent. */
    int evaluate(FuelPrice price);

    /** Evaluate all active alerts against the current price board. Returns the number of notifications sent. */
    int sweep();

    // ---- admin ----
    List<AlertResponse> getAlerts();
    AlertResponse setActive(Long id, boolean active);
    PageResponse<AlertNotificationResponse> getNotifications(int page, int size);
    String channel();
}
