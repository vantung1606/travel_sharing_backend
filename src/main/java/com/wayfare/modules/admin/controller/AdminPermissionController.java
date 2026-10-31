package com.wayfare.modules.admin.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.entity.User;
import com.wayfare.modules.admin.dto.*;
import com.wayfare.modules.admin.service.PermissionService;
import com.wayfare.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminPermissionController {

    private final PermissionService permissionService;
    private final UserRepository userRepository;

    private Long resolveAdminId(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return userRepository.findByEmail(authentication.getName())
                .map(User::getId)
                .orElse(null);
        }
        return null;
    }

    private String extractIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Get all catalog permissions
     */
    @GetMapping("/permissions")
    public ResponseEntity<ApiResponse<List<PermissionDto>>> getAllPermissions() {
        log.info("REST request to get all permissions catalog");
        List<PermissionDto> list = permissionService.getAllPermissions();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh mục quyền thành công", list));
    }

    /**
     * Get all moderators and admins for permission management
     */
    @GetMapping("/moderators")
    public ResponseEntity<ApiResponse<List<ModeratorDto>>> getModerators() {
        log.info("REST request to get all moderators for RBAC management");
        List<ModeratorDto> list = permissionService.getAllModerators();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách người giám sát thành công", list));
    }

    /**
     * Get custom overrides for a user
     */
    @GetMapping("/users/{id}/permissions")
    public ResponseEntity<ApiResponse<List<UserPermissionDto>>> getUserPermissions(@PathVariable Long id) {
        log.info("REST request to get user permissions overrides for user id: {}", id);
        List<UserPermissionDto> list = permissionService.getUserPermissions(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy quyền tùy chỉnh thành công", list));
    }

    /**
     * Get full effective permissions breakdown for a user
     */
    @GetMapping("/users/{id}/effective-permissions")
    public ResponseEntity<ApiResponse<EffectivePermissionsDto>> getEffectivePermissions(@PathVariable Long id) {
        log.info("REST request to calculate effective permissions for user id: {}", id);
        EffectivePermissionsDto result = permissionService.getEffectivePermissions(id);
        return ResponseEntity.ok(ApiResponse.success("Tính toán quyền hiệu lực thành công", result));
    }

    /**
     * Update custom permissions for a user (ALLOW / DENY lists)
     */
    @PutMapping("/users/{id}/permissions")
    public ResponseEntity<ApiResponse<EffectivePermissionsDto>> updateUserPermissions(
        @PathVariable Long id,
        @RequestBody UpdateUserPermissionsRequest request,
        Authentication authentication,
        HttpServletRequest httpRequest
    ) {
        Long adminId = resolveAdminId(authentication);
        String ip = extractIp(httpRequest);
        String userAgent = httpRequest != null ? httpRequest.getHeader("User-Agent") : "Unknown";

        log.info("REST request by adminId={} to update permissions for userId={}: allow={}, deny={}",
            adminId, id, request.getAllow(), request.getDeny());

        EffectivePermissionsDto updated = permissionService.updateUserPermissions(
            adminId, id, request.getAllow(), request.getDeny(), ip, userAgent
        );
        return ResponseEntity.ok(ApiResponse.success("Cập nhật phân quyền người giám sát thành công!", updated));
    }
}
