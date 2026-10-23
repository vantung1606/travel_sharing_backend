package com.wayfare.modules.admin.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.modules.admin.service.AdminUserService;
import com.wayfare.modules.auth.dto.RegisterRequest;
import com.wayfare.modules.user.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminUserController {

    private final AdminUserService adminUserService;

    private String requireAdminEmail(Authentication authentication, String explicitAdminEmail) {
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        if (explicitAdminEmail != null && !explicitAdminEmail.isBlank()) {
            return explicitAdminEmail.trim();
        }
        throw new ResourceNotFoundException("Yêu cầu quyền Quản trị viên để thực hiện thao tác");
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<UserDto>>> getUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "all", required = false, defaultValue = "false") boolean includeAll) {
        log.info("REST request to get users page: keyword='{}', role='{}', status='{}', page={}, size={}",
                keyword, role, status, page, size);
        // includeAll=true: giữ nguyên bộ khi API cũ cần cả danh sách không phân trang
        Pageable pageable = includeAll
                ? Pageable.unpaged()
                : PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserDto> users = adminUserService.getUsers(keyword, role, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách người dùng thành công", users));
    }

    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserMetrics() {
        log.info("REST request to get admin user metrics");
        Map<String, Object> metrics = adminUserService.getUserMetrics();
        return ResponseEntity.ok(ApiResponse.success("Lấy chỉ số người dùng thành công", metrics));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDto>> getUserById(@PathVariable Long id) {
        log.info("REST request to get user detail for id: {}", id);
        UserDto user = adminUserService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin người dùng thành công", user));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UserDto>> toggleUserStatus(
            @PathVariable Long id,
            @RequestParam(value = "adminEmail", required = false) String adminEmail,
            Authentication authentication) {
        String effectiveAdminEmail = requireAdminEmail(authentication, adminEmail);
        log.info("REST request to toggle status for user id: {} by admin: {}", id, effectiveAdminEmail);
        UserDto updated = adminUserService.toggleUserStatus(id, effectiveAdminEmail);
        String action = Boolean.TRUE.equals(updated.getIsLocked()) ? "Đã khóa tài khoản" : "Đã kích hoạt lại tài khoản";
        return ResponseEntity.ok(ApiResponse.success(action + " thành công!", updated));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<ApiResponse<UserDto>> updateUserRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload,
            @RequestParam(value = "adminEmail", required = false) String adminEmail,
            Authentication authentication) {
        String effectiveAdminEmail = requireAdminEmail(authentication, adminEmail);
        String roleName = payload.getOrDefault("role", "ROLE_USER");
        log.info("REST request to update role for user id: {} to '{}' by admin: {}", id, roleName, effectiveAdminEmail);
        UserDto updated = adminUserService.updateUserRole(id, roleName, effectiveAdminEmail);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật phân quyền người dùng thành công!", updated));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserDto>> createUser(
            @RequestBody RegisterRequest request,
            @RequestParam(value = "role", required = false, defaultValue = "ROLE_USER") String role) {
        log.info("REST request to create user: {} with role: {}", request.getEmail(), role);
        UserDto created = adminUserService.createUser(request, role);
        return ResponseEntity.ok(ApiResponse.success("Thêm người dùng mới thành công!", created));
    }

    /**
     * Cấp / gỡ badge Xác thực cho người dùng (trước đây cờ này chỉ set trong
     * DataInitializer — không có quy trình quản trị).
     */
    @PutMapping("/{id}/verified")
    public ResponseEntity<ApiResponse<UserDto>> setUserVerified(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> payload,
            Authentication authentication) {
        String adminEmail = requireAdminEmail(authentication, null);
        boolean verified = Boolean.TRUE.equals(payload.get("verified"));
        log.info("REST request to set verified={} for user id: {} by: {}", verified, id, adminEmail);
        UserDto updated = adminUserService.setUserVerified(id, verified, adminEmail);
        return ResponseEntity.ok(ApiResponse.success(
                verified ? "Đã cấp badge Xác thực cho người dùng!" : "Đã gỡ badge Xác thực!", updated));
    }

    /**
     * Đặt lại mật khẩu: trả về mật khẩu tạm MỘT LẦN — admin tự gửi cho người
     * dùng (không có đường email). Ghi audit log và notification cho user.
     */
    @PutMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetPassword(
            @PathVariable Long id,
            Authentication authentication) {
        String adminEmail = requireAdminEmail(authentication, null);
        log.info("REST request to reset password for user id: {} by: {}", id, adminEmail);
        Map<String, Object> result = adminUserService.resetPassword(id, adminEmail);
        return ResponseEntity.ok(ApiResponse.success("Đã đặt lại mật khẩu. Vui lòng lưu mật khẩu tạm ngay!", result));
    }

    /** Thống kê hoạt động thật của người dùng cho trang chi tiết. */
    @GetMapping("/{id}/profile")
    public ResponseEntity<ApiResponse<UserDto>> getUserProfileFull(@PathVariable Long id) {
        UserDto user = adminUserService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin người dùng thành công", user));
    }

    @GetMapping("/{id}/activity")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserActivity(
            @PathVariable Long id) {
        log.info("REST request to get activity summary for user id: {}", id);
        Map<String, Object> summary = adminUserService.getUserActivitySummary(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thống kê hoạt động thành công", summary));
    }
}
