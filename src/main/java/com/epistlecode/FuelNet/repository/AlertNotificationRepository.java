package com.epistlecode.FuelNet.repository;

import com.epistlecode.FuelNet.model.AlertNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertNotificationRepository extends JpaRepository<AlertNotification, Long> {
    Page<AlertNotification> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByStatus(AlertNotification.Status status);
}
