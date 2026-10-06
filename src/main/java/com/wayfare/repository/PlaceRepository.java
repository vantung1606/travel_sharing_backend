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

    List<Place> findByStatus(String status);

    List<Place> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    @Query("SELECT p FROM Place p WHERE " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:city IS NULL OR LOWER(p.city) LIKE LOWER(CONCAT('%', :city, '%'))) AND " +
           "(:category IS NULL OR LOWER(p.categoryName) LIKE LOWER(CONCAT('%', :category, '%'))) AND " +
           "(:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.address) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Place> searchPlaces(
            @org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("city") String city,
            @org.springframework.data.repository.query.Param("category") String category,
            @org.springframework.data.repository.query.Param("keyword") String keyword
    );
}
