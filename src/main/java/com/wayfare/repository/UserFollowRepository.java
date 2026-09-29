package com.wayfare.repository;

import com.wayfare.entity.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    Optional<UserFollow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    long countByFollowingId(Long followingId); // Number of followers

    long countByFollowerId(Long followerId); // Number of following

    void deleteByFollowerIdAndFollowingId(Long followerId, Long followingId);
}
