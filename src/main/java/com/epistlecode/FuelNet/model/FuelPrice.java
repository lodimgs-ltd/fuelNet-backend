package com.epistlecode.FuelNet.model;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Timestamp;

/**
 * Append-only price record. Every price change inserts a new row; rows are never
 * updated in place. The "current" price for a station/fuel pair is simply the
 * most recent row, and the full table doubles as the audit trail.
 */
@Data
@Entity
@Table(name = "fuel_price", indexes = {
        @Index(name = "idx_fuel_price_station_type_created", columnList = "station_id, fuel_type_id, createdAt")
})
public class FuelPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Timestamp createdAt;

    @Column(nullable = false)
    private double price;

    /** Price that was in effect before this change; null for the first record. */
    private Double previousPrice;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fuel_type_id")
    private FuelType fuelType;

    @ManyToOne
    @JoinColumn(name = "station_id")
    private Station station;

    @ManyToOne
    @JoinColumn(name = "set_by")
    private User setBy;
}
