package com.wayfare.repository;

import com.wayfare.entity.Itinerary;
import com.wayfare.entity.ItineraryDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItineraryDetailRepository extends JpaRepository<ItineraryDetail, Long> {

    List<ItineraryDetail> findByItineraryOrderByDayNumberAscVisitOrderAsc(Itinerary itinerary);

    List<ItineraryDetail> findByItineraryIdOrderByDayNumberAscVisitOrderAsc(Long itineraryId);

    void deleteByItineraryId(Long itineraryId);
}
