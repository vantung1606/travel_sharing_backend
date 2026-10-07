package com.wayfare.repository;

import com.wayfare.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByPlaceIdOrderByCreatedAtDesc(Long placeId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.place.id = :placeId")
    Double calculateAverageRating(@Param("placeId") Long placeId);

    Long countByPlaceId(Long placeId);
}
