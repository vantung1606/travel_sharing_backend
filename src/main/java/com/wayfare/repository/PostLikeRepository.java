package com.wayfare.repository;

import com.wayfare.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    Optional<PostLike> findByUserIdAndPostId(Long userId, Long postId);

    long countByPostId(Long postId);

    void deleteByUserIdAndPostId(Long userId, Long postId);

    void deleteByPostId(Long postId);

    @org.springframework.data.jpa.repository.Query("SELECT pl FROM PostLike pl JOIN FETCH pl.user WHERE pl.post.id = :postId ORDER BY pl.createdAt DESC")
    java.util.List<PostLike> findByPostIdWithUserOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("postId") Long postId);
}
