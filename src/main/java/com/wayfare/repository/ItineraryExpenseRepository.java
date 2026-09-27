package com.wayfare.repository;

import com.wayfare.entity.Itinerary;
import com.wayfare.entity.ItineraryExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ItineraryExpenseRepository extends JpaRepository<ItineraryExpense, Long> {

    List<ItineraryExpense> findByItineraryOrderByCreatedAtDesc(Itinerary itinerary);

    List<ItineraryExpense> findByItineraryIdOrderByCreatedAtDesc(Long itineraryId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM ItineraryExpense e WHERE e.itinerary.id = :itineraryId")
    BigDecimal getTotalExpensesByItineraryId(@Param("itineraryId") Long itineraryId);

    @Query("SELECT e.category, COALESCE(SUM(e.amount), 0) FROM ItineraryExpense e WHERE e.itinerary.id = :itineraryId GROUP BY e.category")
    List<Object[]> getExpensesByCategory(@Param("itineraryId") Long itineraryId);

    void deleteByItineraryId(Long itineraryId);
}
