package com.epistlecode.FuelNet.repository;

import com.epistlecode.FuelNet.model.Station;
import com.epistlecode.FuelNet.model.StationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StationRepository extends JpaRepository<Station, Long> {
    List<Station> findByStatusOrderByNameAsc(StationStatus status);
    List<Station> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCaseAndAddressIgnoreCase(String name, String address);
}
