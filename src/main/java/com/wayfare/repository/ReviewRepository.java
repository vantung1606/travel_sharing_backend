package com.wayfare.repository;

import com.wayfare.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** Danh sách review công khai của 1 địa điểm — chỉ hiện review chưa bị kiểm duyệt ẩn. */
    @Query("SELECT r FROM Review r LEFT JOIN FETCH r.author WHERE r.place.id = :placeId " +
           "AND (r.isHidden IS NULL OR r.isHidden = false) ORDER BY r.createdAt DESC")
    List<Review> findByPlaceIdOrderByCreatedAtDesc(@Param("placeId") Long placeId);

    /**
     * Trang kiểm duyệt ADMIN/MODERATOR: áp dụng tất cả các review (cả bị ẩn)
     * với bộ lọc từ khóa / địa điểm / số sao / trạng thái ẩn.
     */
    @Query("SELECT r FROM Review r LEFT JOIN FETCH r.author LEFT JOIN FETCH r.place WHERE "
           + "(:keyword IS NULL OR :keyword = '' OR LOWER(r.comment) LIKE LOWER(CONCAT('%', :keyword, '%')) "
           + "OR LOWER(r.author.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) "
           + "OR LOWER(r.place.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
           + "AND (:placeId IS NULL OR r.place.id = :placeId) "
           + "AND (:rating IS NULL OR r.rating = :rating) "
           + "AND (:hidden IS NULL OR (:hidden = true AND r.isHidden IS NOT NULL AND r.isHidden = true) "
           + "OR (:hidden = false AND (r.isHidden IS NULL OR r.isHidden = false)))")
    Page<Review> searchAllWithFilters(@Param("keyword") String keyword,
                                      @Param("placeId") Long placeId,
                                      @Param("rating") Integer rating,
                                      @Param("hidden") Boolean hidden,
                                      Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.place.id = :placeId " +
           "AND (r.isHidden IS NULL OR r.isHidden = false)")
    Double calculateAverageRating(@Param("placeId") Long placeId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.place.id = :placeId " +
           "AND (r.isHidden IS NULL OR r.isHidden = false)")
    Long countByPlaceId(@Param("placeId") Long placeId);

    long countByAuthorId(Long authorId);

    long countByIsHiddenTrue();

    @Query("SELECT COUNT(r) FROM Review r WHERE r.isHidden IS NULL OR r.isHidden = false")
    long countByIsHiddenFalseOrNull();
}
