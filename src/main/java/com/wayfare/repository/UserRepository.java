package com.wayfare.repository;

import com.wayfare.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Boolean existsByEmail(String email);
    Boolean existsByHandle(String handle);

    long countByCreatedAtAfter(LocalDateTime dateTime);

    long countByStatus(String status);

    long countByIsLocked(Boolean isLocked);

    @Query("SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r WHERE r.name = :roleName")
    long countByRoleName(@Param("roleName") String roleName);

    @Query("SELECT DISTINCT u FROM User u LEFT JOIN u.roles r WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.handle) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:status IS NULL OR :status = '' OR u.status = :status) " +
           "AND (:roleName IS NULL OR :roleName = '' OR r.name = :roleName) " +
           "ORDER BY u.createdAt DESC")
    List<User> searchUsers(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("roleName") String roleName);

    @Query("SELECT MONTH(u.createdAt) as month, YEAR(u.createdAt) as year, COUNT(u) as count " +
           "FROM User u WHERE u.createdAt >= :startDate " +
           "GROUP BY YEAR(u.createdAt), MONTH(u.createdAt) ORDER BY year, month")
    List<Object[]> countUsersByMonth(@Param("startDate") LocalDateTime startDate);
}
