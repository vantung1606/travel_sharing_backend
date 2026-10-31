package com.wayfare.repository;

import com.wayfare.entity.Role;
import com.wayfare.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findByRole(Role role);
    List<RolePermission> findByRoleId(Long roleId);

    @Query("SELECT rp.permission.code FROM RolePermission rp WHERE rp.role.id IN :roleIds")
    Set<String> findPermissionCodesByRoleIds(@Param("roleIds") Set<Long> roleIds);

    @Query("SELECT rp.permission.code FROM RolePermission rp WHERE rp.role.name = :roleName")
    Set<String> findPermissionCodesByRoleName(@Param("roleName") String roleName);

    boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);
    void deleteByRoleId(Long roleId);
}
