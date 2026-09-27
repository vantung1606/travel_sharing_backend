package com.wayfare.repository;

import com.wayfare.entity.PostReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostReportRepository extends JpaRepository<PostReport, Long> {

    List<PostReport> findByStatusOrderByCreatedAtDesc(String status);

    long countByStatus(String status);

    List<PostReport> findByPostId(Long postId);

    void deleteByPostId(Long postId);
}
