package com.wayfare.repository;

import com.wayfare.entity.User;
import com.wayfare.entity.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {
    List<UserPermission> findByUser(User user);
    List<UserPermission> findByUserId(Long userId);
    Optional<UserPermission> findByUserIdAndPermissionId(Long userId, Long permissionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserPermission up WHERE up.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
