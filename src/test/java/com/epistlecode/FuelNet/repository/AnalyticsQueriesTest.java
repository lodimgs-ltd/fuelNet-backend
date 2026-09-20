package com.epistlecode.FuelNet.repository;

import com.epistlecode.FuelNet.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AnalyticsQueriesTest {

    @Autowired FuelPriceRepository fuelPriceRepository;
    @Autowired FuelTypeRepository fuelTypeRepository;
    @Autowired StationRepository stationRepository;
    @Autowired UserRepository userRepository;

    Station ugbowo;
    FuelType pms;
    User admin;
    User other;

    final Instant now = Instant.now();

    @BeforeEach
    void seed() {
        pms = fuelType("PMS");
        ugbowo = station("Ugbowo");
        admin = user("admin@fuelnet.com", "System Admin");
        other = user("second@fuelnet.com", "Second Admin");

        // 10 days ago: 850 (admin) → 3 days ago: 880 (other) → today: 870 (admin)
        price(ugbowo, pms, 850, now.minus(Duration.ofDays(10)), admin);
        price(ugbowo, pms, 880, now.minus(Duration.ofDays(3)), other);
        price(ugbowo, pms, 870, now, admin);
    }

    @Test
    void boardAsOfReturnsPriceInEffectAtThatTime() {
        List<FuelPrice> weekAgo = fuelPriceRepository.findBoardAsOf(Timestamp.from(now.minus(Duration.ofDays(7))));
        assertThat(weekAgo).hasSize(1);
        assertThat(weekAgo.get(0).getPrice()).isEqualTo(850.0);

        List<FuelPrice> yesterday = fuelPriceRepository.findBoardAsOf(Timestamp.from(now.minus(Duration.ofDays(1))));
        assertThat(yesterday.get(0).getPrice()).isEqualTo(880.0);

        assertThat(fuelPriceRepository.findBoardAsOf(Timestamp.from(now.minus(Duration.ofDays(30))))).isEmpty();
    }

    @Test
    void countsChangesSince() {
        assertThat(fuelPriceRepository.countByCreatedAtAfter(Timestamp.from(now.minus(Duration.ofDays(7))))).isEqualTo(2);
        assertThat(fuelPriceRepository.countByFuelTypeAndCreatedAtAfter(pms, Timestamp.from(now.minus(Duration.ofDays(30))))).isEqualTo(3);
    }

    @Test
    void countsChangesPerDay() {
        List<Object[]> rows = fuelPriceRepository.countChangesPerDay(Timestamp.from(now.minus(Duration.ofDays(30))));
        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(r -> assertThat(((Number) r[1]).longValue()).isEqualTo(1L));
    }

    @Test
    void countsChangesPerAdminMostActiveFirst() {
        List<Object[]> rows = fuelPriceRepository.countChangesPerAdmin();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)[2]).isEqualTo("admin@fuelnet.com");
        assertThat(((Number) rows.get(0)[3]).longValue()).isEqualTo(2L);
        assertThat(((Number) rows.get(1)[3]).longValue()).isEqualTo(1L);
    }

    // ---- helpers

    private FuelType fuelType(String name) {
        FuelType t = new FuelType();
        t.setName(name);
        t.setCreatedAt(new Timestamp(0));
        return fuelTypeRepository.save(t);
    }

    private Station station(String name) {
        Station s = new Station();
        s.setName(name);
        s.setAddress("addr");
        s.setState("Edo");
        s.setStatus(StationStatus.ACTIVE);
        s.setCreatedAt(new Timestamp(0));
        return stationRepository.save(s);
    }

    private User user(String email, String name) {
        User u = new User();
        u.setEmail(email);
        u.setFullName(name);
        u.setPassword("x");
        u.setRole(UserRole.ROLE_ADMIN);
        u.setStatus(UserStatus.ENABLED);
        u.setCreatedAt(new Timestamp(0));
        return userRepository.save(u);
    }

    private void price(Station s, FuelType t, double price, Instant at, User by) {
        FuelPrice p = new FuelPrice();
        p.setStation(s);
        p.setFuelType(t);
        p.setPrice(price);
        p.setSetBy(by);
        p.setCreatedAt(Timestamp.from(at));
        fuelPriceRepository.save(p);
    }
}
