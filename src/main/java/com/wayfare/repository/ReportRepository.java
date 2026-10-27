package com.wayfare.repository;

import com.wayfare.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByStatusOrderByCreatedAtDesc(String status);

    List<Report> findByTargetTypeOrderByCreatedAtDesc(String targetType);

    List<Report> findByTargetTypeAndStatusOrderByCreatedAtDesc(String targetType, String status);

    List<Report> findByTargetTypeAndTargetId(String targetType, Long targetId);

    List<Report> findAllByOrderByCreatedAtDesc();

    long countByStatus(String status);

    long countByTargetTypeAndStatus(String targetType, String status);

    long countByTargetTypeAndTargetIdAndStatus(String targetType, Long targetId, String status);

    void deleteByTargetTypeAndTargetId(String targetType, Long targetId);
}
