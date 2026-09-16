package com.wayfare.repository;

import com.wayfare.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaceRepository extends JpaRepository<Place, Long> {

    @Query("SELECT AVG(p.averageRating) FROM Place p WHERE p.averageRating > 0")
    Double findOverallAverageRating();

    @Query("SELECT p FROM Place p ORDER BY p.reviewCount DESC")
    List<Place> findTopPlacesByReviews(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT p FROM Place p ORDER BY p.averageRating DESC")
    List<Place> findTopPlacesByRating(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT p.city, COUNT(p) as cnt FROM Place p GROUP BY p.city ORDER BY cnt DESC")
    List<Object[]> countPlacesByCity();
}
