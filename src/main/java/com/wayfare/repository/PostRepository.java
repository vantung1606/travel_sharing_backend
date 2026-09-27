package com.wayfare.repository;

import com.wayfare.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    long countByCreatedAtAfter(LocalDateTime dateTime);

    @Query("SELECT MONTH(p.createdAt) as month, YEAR(p.createdAt) as year, COUNT(p) as count " +
           "FROM Post p WHERE p.createdAt >= :startDate " +
           "GROUP BY YEAR(p.createdAt), MONTH(p.createdAt) ORDER BY year, month")
    List<Object[]> countPostsByMonth(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT SUM(p.likeCount) FROM Post p")
    Long sumAllLikes();

    @Query("SELECT SUM(p.commentCount) FROM Post p")
    Long sumAllComments();

    List<Post> findByStatusOrderByCreatedAtDesc(String status);

    long countByStatus(String status);

    List<Post> findByReportsCountGreaterThanOrderByReportsCountDesc(Integer count);

    @Query("SELECT p FROM Post p WHERE " +
           "(:keyword IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.author.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.locationTag) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "(:status IS NULL OR p.status = :status) " +
           "ORDER BY p.createdAt DESC")
    List<Post> searchPosts(@Param("keyword") String keyword, @Param("status") String status);
}
