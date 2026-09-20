package com.epistlecode.FuelNet.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoUtilsTest {

    @Test
    void distanceBetweenSamePointIsZero() {
        assertThat(GeoUtils.distanceKm(6.399, 5.61, 6.399, 5.61)).isZero();
    }

    @Test
    void beninCityToLagosIsRoughly260Km() {
        // UNIBEN main gate → Ikeja
        double d = GeoUtils.distanceKm(6.3990, 5.6100, 6.6018, 3.3515);
        assertThat(d).isCloseTo(250, within(15.0));
    }

    @Test
    void distanceIsSymmetric() {
        double ab = GeoUtils.distanceKm(6.3990, 5.6100, 6.6018, 3.3515);
        double ba = GeoUtils.distanceKm(6.6018, 3.3515, 6.3990, 5.6100);
        assertThat(ab).isEqualTo(ba);
    }

    @Test
    void round1KeepsOneDecimal() {
        assertThat(GeoUtils.round1(12.3456)).isEqualTo(12.3);
        assertThat(GeoUtils.round1(0.05)).isEqualTo(0.1);
    }
}
