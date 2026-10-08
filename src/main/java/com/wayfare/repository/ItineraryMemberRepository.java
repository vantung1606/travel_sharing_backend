package com.wayfare.repository;

import com.wayfare.entity.Itinerary;
import com.wayfare.entity.ItineraryMember;
import com.wayfare.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItineraryMemberRepository extends JpaRepository<ItineraryMember, Long> {

    List<ItineraryMember> findByItinerary(Itinerary itinerary);

    List<ItineraryMember> findByItineraryId(Long itineraryId);

    Optional<ItineraryMember> findByItineraryAndUser(Itinerary itinerary, User user);

    Optional<ItineraryMember> findByItineraryIdAndUserId(Long itineraryId, Long userId);

    boolean existsByItineraryIdAndUserId(Long itineraryId, Long userId);

    void deleteByItineraryIdAndUserId(Long itineraryId, Long userId);

    void deleteByItineraryId(Long itineraryId);
}
