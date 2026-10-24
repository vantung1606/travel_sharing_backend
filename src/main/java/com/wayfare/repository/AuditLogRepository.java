package com.wayfare.repository;

import com.wayfare.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:category IS NULL OR UPPER(a.category) = UPPER(:category)) AND " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:action IS NULL OR UPPER(a.action) = UPPER(:action)) AND " +
           "(:kw IS NULL OR " +
           " LOWER(a.action) LIKE LOWER(CONCAT('%', :kw, '%')) OR " +
           " LOWER(a.details) LIKE LOWER(CONCAT('%', :kw, '%')) OR " +
           " LOWER(a.userName) LIKE LOWER(CONCAT('%', :kw, '%')) OR " +
           " LOWER(a.userEmail) LIKE LOWER(CONCAT('%', :kw, '%')) OR " +
           " LOWER(a.ipAddress) LIKE LOWER(CONCAT('%', :kw, '%'))) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLog> searchLogs(
            @Param("kw") String keyword,
            @Param("userId") Long userId,
            @Param("action") String action,
            @Param("category") String category,
            Pageable pageable
    );

    @Query("SELECT DISTINCT a.action FROM AuditLog a ORDER BY a.action")
    List<String> findDistinctActions();

    long countByCategoryIgnoreCase(String category);

    long countByCreatedAtAfter(LocalDateTime dateTime);
}
