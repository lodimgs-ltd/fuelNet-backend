package com.epistlecode.FuelNet.model;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Timestamp;

/** Delivery log: one row per notification sent (or attempted) for an alert. */
@Entity
@Table(name = "alert_notifications")
@Data
public class AlertNotification {

    public enum Status { SENT, FAILED, LOGGED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "alert_id")
    private PriceAlert alert;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fuel_price_id")
    private FuelPrice fuelPrice;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(length = 500)
    private String error;

    @Column(nullable = false)
    private Timestamp createdAt;
}
