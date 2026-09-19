package com.epistlecode.FuelNet.repository;

import com.epistlecode.FuelNet.model.FuelType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FuelTypeRepository extends JpaRepository<FuelType, Long> {
    FuelType findByName(String name);
    Optional<FuelType> findByNameIgnoreCase(String name);
}
