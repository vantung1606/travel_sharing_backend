package com.wayfare.controller;

import com.wayfare.dto.ApiResponse;
import com.wayfare.dto.RegisterRequest;
import com.wayfare.dto.UserDto;
import com.wayfare.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Slf4j
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDto>>> getUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "status", required = false) String status) {
        log.info("REST request to get users: keyword='{}', role='{}', status='{}'", keyword, role, status);
        List<UserDto> users = adminUserService.getUsers(keyword, role, status);
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
            @RequestParam(value = "adminEmail", required = false, defaultValue = "admin@gmail.com") String adminEmail) {
        log.info("REST request to toggle status for user id: {} by admin: {}", id, adminEmail);
        UserDto updated = adminUserService.toggleUserStatus(id, adminEmail);
        String action = updated.getIsLocked() ? "Đã khóa tài khoản" : "Đã kích hoạt lại tài khoản";
        return ResponseEntity.ok(ApiResponse.success(action + " thành công!", updated));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<ApiResponse<UserDto>> updateUserRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload,
            @RequestParam(value = "adminEmail", required = false, defaultValue = "admin@gmail.com") String adminEmail) {
        String roleName = payload.getOrDefault("role", "ROLE_USER");
        log.info("REST request to update role for user id: {} to '{}' by admin: {}", id, roleName, adminEmail);
        UserDto updated = adminUserService.updateUserRole(id, roleName, adminEmail);
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
}
