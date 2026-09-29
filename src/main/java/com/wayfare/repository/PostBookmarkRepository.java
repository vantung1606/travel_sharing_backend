package com.wayfare.repository;

import com.wayfare.entity.PostBookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostBookmarkRepository extends JpaRepository<PostBookmark, Long> {

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    Optional<PostBookmark> findByUserIdAndPostId(Long userId, Long postId);

    long countByPostId(Long postId);

    long countByUserId(Long userId);

    void deleteByUserIdAndPostId(Long userId, Long postId);

    List<PostBookmark> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT pb.post.id FROM PostBookmark pb WHERE pb.user.id = :userId ORDER BY pb.createdAt DESC")
    List<Long> findBookmarkedPostIdsByUserId(@Param("userId") Long userId);
}
