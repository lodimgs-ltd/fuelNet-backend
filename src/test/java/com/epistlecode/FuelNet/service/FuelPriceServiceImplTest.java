package com.epistlecode.FuelNet.service;

import com.epistlecode.FuelNet.dto.FuelPriceResponse;
import com.epistlecode.FuelNet.dto.RecordPriceRequest;
import com.epistlecode.FuelNet.exception.ResourceNotFoundException;
import com.epistlecode.FuelNet.model.FuelPrice;
import com.epistlecode.FuelNet.model.FuelType;
import com.epistlecode.FuelNet.model.Station;
import com.epistlecode.FuelNet.model.User;
import com.epistlecode.FuelNet.repository.FuelPriceRepository;
import com.epistlecode.FuelNet.repository.FuelTypeRepository;
import com.epistlecode.FuelNet.repository.StationRepository;
import com.epistlecode.FuelNet.service.impl.FuelPriceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FuelPriceServiceImplTest {

    @Mock FuelPriceRepository fuelPriceRepository;
    @Mock FuelTypeRepository fuelTypeRepository;
    @Mock StationRepository stationRepository;
    @Mock org.springframework.context.ApplicationEventPublisher events;

    FuelPriceServiceImpl service;

    Station station;
    FuelType pms;
    User admin;

    @BeforeEach
    void setUp() {
        service = new FuelPriceServiceImpl(fuelPriceRepository, fuelTypeRepository, stationRepository, events);

        station = new Station();
        station.setId(1L);
        station.setName("FuelNet Ugbowo");

        pms = new FuelType();
        pms.setId(10L);
        pms.setName("PMS");

        admin = new User();
        admin.setId(99L);
        admin.setFullName("System Admin");
        admin.setEmail("admin@fuelnet.com");
    }

    private RecordPriceRequest request(double price) {
        RecordPriceRequest req = new RecordPriceRequest();
        req.setStationId(1L);
        req.setFuelType("PMS");
        req.setPrice(price);
        return req;
    }

    @Test
    void recordPriceAppendsNewRowAndCapturesPreviousPrice() {
        FuelPrice existing = new FuelPrice();
        existing.setPrice(865.0);
        existing.setStation(station);
        existing.setFuelType(pms);

        when(stationRepository.findById(1L)).thenReturn(Optional.of(station));
        when(fuelTypeRepository.findByNameIgnoreCase("PMS")).thenReturn(Optional.of(pms));
        when(fuelPriceRepository.findFirstByStationAndFuelTypeOrderByCreatedAtDesc(station, pms))
                .thenReturn(Optional.of(existing));
        when(fuelPriceRepository.save(any(FuelPrice.class))).thenAnswer(inv -> {
            FuelPrice p = inv.getArgument(0);
            p.setId(42L);
            return p;
        });

        FuelPriceResponse response = service.recordPrice(request(900.0), admin);

        ArgumentCaptor<FuelPrice> captor = ArgumentCaptor.forClass(FuelPrice.class);
        verify(fuelPriceRepository).save(captor.capture());
        FuelPrice saved = captor.getValue();

        // a brand-new row, never the existing one mutated
        assertThat(saved).isNotSameAs(existing);
        assertThat(saved.getId()).isEqualTo(42L);
        assertThat(saved.getPrice()).isEqualTo(900.0);
        assertThat(saved.getPreviousPrice()).isEqualTo(865.0);
        assertThat(saved.getSetBy()).isSameAs(admin);
        assertThat(saved.getCreatedAt()).isNotNull();

        assertThat(response.change()).isEqualTo(35.0);
        assertThat(response.changePercent()).isEqualTo(4.05);
        assertThat(response.setByEmail()).isEqualTo("admin@fuelnet.com");
    }

    @Test
    void recordPriceForFirstEverPriceHasNoPreviousPrice() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(station));
        when(fuelTypeRepository.findByNameIgnoreCase("PMS")).thenReturn(Optional.of(pms));
        when(fuelPriceRepository.findFirstByStationAndFuelTypeOrderByCreatedAtDesc(station, pms))
                .thenReturn(Optional.empty());
        when(fuelPriceRepository.save(any(FuelPrice.class))).thenAnswer(inv -> inv.getArgument(0));

        FuelPriceResponse response = service.recordPrice(request(865.0), admin);

        assertThat(response.previousPrice()).isNull();
        assertThat(response.change()).isNull();
        assertThat(response.changePercent()).isNull();
    }

    @Test
    void recordPriceRejectsUnknownStation() {
        when(stationRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordPrice(request(900.0), admin))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Station not found");
        verify(fuelPriceRepository, never()).save(any());
    }

    @Test
    void recordPriceRejectsUnknownFuelType() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(station));
        when(fuelTypeRepository.findByNameIgnoreCase("PMS")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordPrice(request(900.0), admin))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Unknown fuel type");
    }

    @Test
    void historyLimitIsCappedAt500() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(station));
        when(fuelTypeRepository.findByNameIgnoreCase("PMS")).thenReturn(Optional.of(pms));
        when(fuelPriceRepository.findByStationAndFuelTypeOrderByCreatedAtDesc(eq(station), eq(pms), any()))
                .thenReturn(java.util.List.of());

        service.getHistory(1L, "PMS", 10_000);

        ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(fuelPriceRepository).findByStationAndFuelTypeOrderByCreatedAtDesc(eq(station), eq(pms), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(500);
    }

    @Test
    void responseMappingComputesRoundedChange() {
        FuelPrice p = new FuelPrice();
        p.setId(1L);
        p.setStation(station);
        p.setFuelType(pms);
        p.setPrice(1000.0);
        p.setPreviousPrice(1030.0);
        p.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        FuelPriceResponse r = FuelPriceResponse.from(p);
        assertThat(r.change()).isEqualTo(-30.0);
        assertThat(r.changePercent()).isEqualTo(-2.91);
        assertThat(r.stationName()).isEqualTo("FuelNet Ugbowo");
        assertThat(r.setByName()).isNull();
    }
}
