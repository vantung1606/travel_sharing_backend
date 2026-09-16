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
}
