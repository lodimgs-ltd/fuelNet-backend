package com.epistlecode.FuelNet.repository;

import com.epistlecode.FuelNet.model.FuelPrice;
import com.epistlecode.FuelNet.model.FuelType;
import com.epistlecode.FuelNet.model.Station;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FuelPriceRepository extends JpaRepository<FuelPrice, Long> {

    Optional<FuelPrice> findFirstByStationAndFuelTypeOrderByCreatedAtDesc(Station station, FuelType fuelType);

    /** Latest record for every (station, fuelType) pair — the "current price board". */
    @Query("""
            select p from FuelPrice p
            join fetch p.fuelType
            join fetch p.station
            left join fetch p.setBy
            where p.id in (
                select max(q.id) from FuelPrice q
                where q.station is not null
                group by q.station.id, q.fuelType.id
            )
            order by p.station.name asc, p.fuelType.name asc
            """)
    List<FuelPrice> findCurrentPrices();

    /** Latest record for every fuelType at a single station. */
    @Query("""
            select p from FuelPrice p
            join fetch p.fuelType
            join fetch p.station
            left join fetch p.setBy
            where p.id in (
                select max(q.id) from FuelPrice q
                where q.station.id = :stationId
                group by q.fuelType.id
            )
            order by p.fuelType.name asc
            """)
    List<FuelPrice> findCurrentPricesByStation(@Param("stationId") Long stationId);

    List<FuelPrice> findByStationAndFuelTypeOrderByCreatedAtDesc(Station station, FuelType fuelType, Pageable pageable);

    List<FuelPrice> findByFuelTypeOrderByCreatedAtDesc(FuelType fuelType, Pageable pageable);

    Page<FuelPrice> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<FuelPrice> findByStationOrderByCreatedAtDesc(Station station, Pageable pageable);
}
