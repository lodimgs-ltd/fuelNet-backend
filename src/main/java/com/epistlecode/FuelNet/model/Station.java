package com.epistlecode.FuelNet.model;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Timestamp;

/**
 * A physical filling station in the FuelNet network. Every price record belongs
 * to exactly one station, which is what lets the platform compare prices across
 * the network rather than publishing a single global figure.
 */
@Entity
@Table(name = "stations")
@Data
public class Station {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    private String city;

    private String state;

    private Double latitude;

    private Double longitude;

    @Enumerated(EnumType.STRING)
    private StationStatus status;

    private Timestamp createdAt;
}
