package com.epistlecode.FuelNet.repository;

import com.epistlecode.FuelNet.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class FuelPriceRepositoryTest {

    @Autowired FuelPriceRepository fuelPriceRepository;
    @Autowired FuelTypeRepository fuelTypeRepository;
    @Autowired StationRepository stationRepository;

    Station ugbowo;
    Station sapele;
    FuelType pms;
    FuelType diesel;

    @BeforeEach
    void seed() {
        pms = fuelType("PMS");
        diesel = fuelType("Diesel");
        ugbowo = station("FuelNet Ugbowo");
        sapele = station("FuelNet Sapele Road");

        // Ugbowo PMS: 865 → 880 → 870   (current should be 870)
        price(ugbowo, pms, 865, 1000);
        price(ugbowo, pms, 880, 2000);
        price(ugbowo, pms, 870, 3000);
        // Ugbowo Diesel: 1050          (current 1050)
        price(ugbowo, diesel, 1050, 1500);
        // Sapele PMS: 900 → 895        (current 895)
        price(sapele, pms, 900, 1000);
        price(sapele, pms, 895, 4000);
    }

    @Test
    void findCurrentPricesReturnsOnlyLatestRowPerStationAndFuel() {
        List<FuelPrice> current = fuelPriceRepository.findCurrentPrices();

        assertThat(current).hasSize(3);
        assertThat(current)
                .extracting(p -> p.getStation().getName() + "/" + p.getFuelType().getName(), FuelPrice::getPrice)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("FuelNet Ugbowo/PMS", 870.0),
                        org.assertj.core.groups.Tuple.tuple("FuelNet Ugbowo/Diesel", 1050.0),
                        org.assertj.core.groups.Tuple.tuple("FuelNet Sapele Road/PMS", 895.0));
    }

    @Test
    void findCurrentPricesByStationScopesToThatStation() {
        List<FuelPrice> current = fuelPriceRepository.findCurrentPricesByStation(sapele.getId());
        assertThat(current).hasSize(1);
        assertThat(current.get(0).getPrice()).isEqualTo(895.0);
    }

    @Test
    void latestRecordLookupReturnsNewestByCreatedAt() {
        var latest = fuelPriceRepository.findFirstByStationAndFuelTypeOrderByCreatedAtDesc(ugbowo, pms);
        assertThat(latest).isPresent();
        assertThat(latest.get().getPrice()).isEqualTo(870.0);
    }

    @Test
    void historyIsNewestFirstAndRespectsLimit() {
        List<FuelPrice> history = fuelPriceRepository
                .findByStationAndFuelTypeOrderByCreatedAtDesc(ugbowo, pms, PageRequest.of(0, 2));
        assertThat(history).extracting(FuelPrice::getPrice).containsExactly(870.0, 880.0);
    }

    // ---------------------------------------------------------------- helpers

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
        s.setStatus(StationStatus.ACTIVE);
        s.setCreatedAt(new Timestamp(0));
        return stationRepository.save(s);
    }

    private void price(Station s, FuelType t, double price, long createdAtMillis) {
        FuelPrice p = new FuelPrice();
        p.setStation(s);
        p.setFuelType(t);
        p.setPrice(price);
        p.setCreatedAt(new Timestamp(createdAtMillis));
        fuelPriceRepository.save(p);
    }
}
