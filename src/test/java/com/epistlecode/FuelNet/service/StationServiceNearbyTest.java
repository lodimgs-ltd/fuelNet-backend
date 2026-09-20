package com.epistlecode.FuelNet.service;

import com.epistlecode.FuelNet.dto.NearbyStationResponse;
import com.epistlecode.FuelNet.model.FuelPrice;
import com.epistlecode.FuelNet.model.FuelType;
import com.epistlecode.FuelNet.model.Station;
import com.epistlecode.FuelNet.model.StationStatus;
import com.epistlecode.FuelNet.repository.FuelPriceRepository;
import com.epistlecode.FuelNet.repository.StationRepository;
import com.epistlecode.FuelNet.service.impl.StationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class StationServiceNearbyTest {

    @Mock StationRepository stationRepository;
    @Mock FuelPriceRepository fuelPriceRepository;

    StationServiceImpl service;

    // Benin City cluster + one in Lagos + one without coordinates
    Station ugbowo = station(1L, "Ugbowo", 6.3990, 5.6100);
    Station sapele = station(2L, "Sapele Road", 6.3240, 5.6420);
    Station airport = station(3L, "Airport Road", 6.3170, 5.6000);
    Station ikeja = station(4L, "Ikeja", 6.6018, 3.3515);
    Station noCoords = station(5L, "No coords", null, null);

    FuelType pms = fuelType(10L, "PMS");
    FuelType diesel = fuelType(11L, "Diesel");

    @BeforeEach
    void setUp() {
        service = new StationServiceImpl(stationRepository, fuelPriceRepository);
        lenient().when(stationRepository.findByStatusOrderByNameAsc(StationStatus.ACTIVE))
                .thenReturn(List.of(airport, ikeja, noCoords, sapele, ugbowo));
        lenient().when(fuelPriceRepository.findCurrentPrices()).thenReturn(List.of(
                price(ugbowo, pms, 870), price(ugbowo, diesel, 1050),
                price(sapele, pms, 860),
                price(airport, pms, 880),
                price(ikeja, pms, 855)
        ));
    }

    @Test
    void returnsStationsWithinRadiusNearestFirst() {
        // from UNIBEN gate, 25 km radius: the three Benin stations; Lagos is ~250 km away
        List<NearbyStationResponse> out = service.findNearby(6.3990, 5.6100, 25, null, 20);

        assertThat(out).extracting(NearbyStationResponse::name).containsExactly("Ugbowo", "Sapele Road", "Airport Road");
        assertThat(out.get(0).distanceKm()).isZero();
        // distances are non-decreasing
        for (int i = 1; i < out.size(); i++) {
            assertThat(out.get(i).distanceKm()).isGreaterThanOrEqualTo(out.get(i - 1).distanceKm());
        }
        assertThat(out.get(0).prices()).containsEntry("PMS", 870.0).containsEntry("Diesel", 1050.0);
        assertThat(out).noneMatch(s -> s.name().equals("No coords"));
    }

    @Test
    void fuelTypeFilterSortsByThatFuelsPriceThenDistance() {
        List<NearbyStationResponse> out = service.findNearby(6.3990, 5.6100, 25, "pms", 20);

        assertThat(out).extracting(NearbyStationResponse::name).containsExactly("Sapele Road", "Ugbowo", "Airport Road");
    }

    @Test
    void fuelTypeFilterDropsStationsWithoutThatFuel() {
        List<NearbyStationResponse> out = service.findNearby(6.3990, 5.6100, 25, "Diesel", 20);
        assertThat(out).extracting(NearbyStationResponse::name).containsExactly("Ugbowo");
    }

    @Test
    void largeRadiusIncludesLagosAndLimitIsHonoured() {
        List<NearbyStationResponse> out = service.findNearby(6.3990, 5.6100, 500, null, 2);
        assertThat(out).hasSize(2);
        assertThat(service.findNearby(6.3990, 5.6100, 500, null, 20))
                .extracting(NearbyStationResponse::name).contains("Ikeja");
    }

    @Test
    void rejectsInvalidCoordinates() {
        assertThatThrownBy(() -> service.findNearby(95, 0, 10, null, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- helpers

    private static Station station(long id, String name, Double lat, Double lng) {
        Station s = new Station();
        s.setId(id);
        s.setName(name);
        s.setAddress("addr");
        s.setLatitude(lat);
        s.setLongitude(lng);
        s.setStatus(StationStatus.ACTIVE);
        return s;
    }

    private static FuelType fuelType(long id, String name) {
        FuelType t = new FuelType();
        t.setId(id);
        t.setName(name);
        return t;
    }

    private static FuelPrice price(Station s, FuelType t, double price) {
        FuelPrice p = new FuelPrice();
        p.setStation(s);
        p.setFuelType(t);
        p.setPrice(price);
        return p;
    }
}
