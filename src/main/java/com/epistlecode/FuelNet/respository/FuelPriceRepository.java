package com.epistlecode.FuelNet.respository;

import com.epistlecode.FuelNet.model.FuelPrice;
import com.epistlecode.FuelNet.model.FuelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FuelPriceRepository extends JpaRepository<FuelPrice, Long> {
    FuelPrice findByFuelType(FuelType type);
}
