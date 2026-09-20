package com.epistlecode.FuelNet.repository;

import com.epistlecode.FuelNet.model.FuelPrice;
import com.epistlecode.FuelNet.model.FuelType;
import com.epistlecode.FuelNet.model.Station;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
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

    /** The price board as it stood at {@code asOf}: latest row per (station, fuelType) created on or before it. */
    @Query("""
            select p from FuelPrice p
            join fetch p.fuelType
            join fetch p.station
            where p.id in (
                select max(q.id) from FuelPrice q
                where q.station is not null and q.createdAt <= :asOf
                group by q.station.id, q.fuelType.id
            )
            """)
    List<FuelPrice> findBoardAsOf(@Param("asOf") Timestamp asOf);

    /** Number of price changes per calendar day since {@code since}: [java.sql.Date day, Long count]. */
    @Query("""
            select cast(p.createdAt as date), count(p)
            from FuelPrice p
            where p.createdAt >= :since
            group by cast(p.createdAt as date)
            order by cast(p.createdAt as date) asc
            """)
    List<Object[]> countChangesPerDay(@Param("since") Timestamp since);

    /** Changes per admin: [userId, fullName, email, count, lastChangeAt]. */
    @Query("""
            select u.id, u.fullName, u.email, count(p), max(p.createdAt)
            from FuelPrice p join p.setBy u
            group by u.id, u.fullName, u.email
            order by count(p) desc
            """)
    List<Object[]> countChangesPerAdmin();

    long countByCreatedAtAfter(Timestamp since);

    long countByFuelTypeAndCreatedAtAfter(FuelType fuelType, Timestamp since);

    List<FuelPrice> findByStationAndFuelTypeOrderByCreatedAtDesc(Station station, FuelType fuelType, Pageable pageable);

    List<FuelPrice> findByFuelTypeOrderByCreatedAtDesc(FuelType fuelType, Pageable pageable);

    Page<FuelPrice> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<FuelPrice> findByStationOrderByCreatedAtDesc(Station station, Pageable pageable);
}
