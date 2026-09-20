package com.epistlecode.FuelNet.model;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Timestamp;

/**
 * A subscription: "email me when <fuelType> at <station | any station> goes
 * BELOW/ABOVE <threshold>". Alerts are evaluated whenever a price is recorded
 * and by a periodic sweep. To avoid spamming, an alert re-fires only after the
 * condition has stopped being true and become true again.
 */
@Entity
@Table(name = "price_alerts", indexes = {
        @Index(name = "idx_price_alert_active", columnList = "active"),
        @Index(name = "idx_price_alert_token", columnList = "unsubscribeToken", unique = true)
})
@Data
public class PriceAlert {

    public enum Condition { BELOW, ABOVE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fuel_type_id")
    private FuelType fuelType;

    /** Null means "any station in the network". */
    @ManyToOne
    @JoinColumn(name = "station_id")
    private Station station;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Condition condition;

    @Column(nullable = false)
    private double threshold;

    @Column(nullable = false)
    private boolean active = true;

    /** True while the condition currently holds; prevents repeat notifications. */
    @Column(nullable = false)
    private boolean triggered = false;

    @Column(nullable = false, length = 64)
    private String unsubscribeToken;

    private Timestamp lastNotifiedAt;

    @Column(nullable = false)
    private Timestamp createdAt;
}
