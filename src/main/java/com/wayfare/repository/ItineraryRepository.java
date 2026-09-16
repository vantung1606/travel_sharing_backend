package com.wayfare.repository;

import com.wayfare.entity.Itinerary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ItineraryRepository extends JpaRepository<Itinerary, Long> {

    long countByCreatedAtAfter(LocalDateTime dateTime);

    long countByStatus(String status);

    @Query("SELECT MONTH(i.createdAt) as month, YEAR(i.createdAt) as year, COUNT(i) as count " +
           "FROM Itinerary i WHERE i.createdAt >= :startDate " +
           "GROUP BY YEAR(i.createdAt), MONTH(i.createdAt) ORDER BY year, month")
    List<Object[]> countItinerariesByMonth(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT i.destination, COUNT(i) as cnt FROM Itinerary i GROUP BY i.destination ORDER BY cnt DESC")
    List<Object[]> findTopDestinations(org.springframework.data.domain.Pageable pageable);
}
