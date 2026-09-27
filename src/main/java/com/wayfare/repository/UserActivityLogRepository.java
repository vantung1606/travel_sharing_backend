package com.wayfare.repository;

import com.wayfare.entity.UserActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long> {

    @Query("SELECT l FROM UserActivityLog l LEFT JOIN l.user u WHERE " +
           "(:userId IS NULL OR (u IS NOT NULL AND u.id = :userId)) AND " +
           "(:action IS NULL OR :action = '' OR l.action = :action) AND " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           " LOWER(l.action) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(l.details) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(l.ipAddress) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " (u IS NOT NULL AND (" +
           "   LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "   LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "   LOWER(u.handle) LIKE LOWER(CONCAT('%', :keyword, '%'))" +
           " ))) " +
           "ORDER BY l.createdAt DESC")
    Page<UserActivityLog> searchLogs(
            @Param("keyword") String keyword,
            @Param("userId") Long userId,
            @Param("action") String action,
            Pageable pageable);

    @Query("SELECT DISTINCT l.action FROM UserActivityLog l WHERE l.action IS NOT NULL ORDER BY l.action ASC")
    List<String> findDistinctActions();

    long countByCreatedAtAfter(LocalDateTime dateTime);
}
