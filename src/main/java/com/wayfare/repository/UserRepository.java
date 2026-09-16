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

    @Query("SELECT MONTH(u.createdAt) as month, YEAR(u.createdAt) as year, COUNT(u) as count " +
           "FROM User u WHERE u.createdAt >= :startDate " +
           "GROUP BY YEAR(u.createdAt), MONTH(u.createdAt) ORDER BY year, month")
    List<Object[]> countUsersByMonth(@Param("startDate") LocalDateTime startDate);
}
