package com.wayfare.repository;

import com.wayfare.entity.SystemReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemReportRepository extends JpaRepository<SystemReport, Long> {
    List<SystemReport> findByStatusOrderByCreatedAtDesc(String status);
    List<SystemReport> findByTargetTypeOrderByCreatedAtDesc(String targetType);
    List<SystemReport> findAllByOrderByCreatedAtDesc();
    long countByStatus(String status);
}
