package com.epistlecode.FuelNet.repository;

import com.epistlecode.FuelNet.model.PriceAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {
    List<PriceAlert> findByActiveTrue();
    List<PriceAlert> findAllByOrderByCreatedAtDesc();
    Optional<PriceAlert> findByUnsubscribeToken(String token);
    long countByEmailIgnoreCaseAndActiveTrue(String email);
    long countByActiveTrue();
}
