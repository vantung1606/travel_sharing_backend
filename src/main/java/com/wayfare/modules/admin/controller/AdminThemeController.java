package com.wayfare.modules.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayfare.common.dto.ApiResponse;
import com.wayfare.entity.ThemeVersion;
import com.wayfare.entity.User;
import com.wayfare.modules.admin.service.ThemeService;
import com.wayfare.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/theme")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'THEME_VIEW')")
@Slf4j
public class AdminThemeController {

    private final ThemeService themeService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
     * Get theme status (published version + active draft)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTheme() {
        ThemeVersion published = themeService.getPublishedTheme();
        ThemeVersion draft = themeService.getDraftTheme();

        Map<String, Object> res = new HashMap<>();
        try {
            res.put("published", objectMapper.readValue(published.getConfigJson(), Map.class));
            res.put("publishedVersion", published.getVersionNo());
            res.put("publishedAt", published.getPublishedAt());

            res.put("draft", objectMapper.readValue(draft.getConfigJson(), Map.class));
            res.put("draftVersion", draft.getVersionNo());
            res.put("draftStatus", draft.getStatus());
            res.put("draftNote", draft.getNote());
        } catch (Exception e) {
            log.error("Lỗi khi parse theme: {}", e.getMessage());
        }
        return ResponseEntity.ok(ApiResponse.success("Lấy cấu hình theme thành công", res));
    }

    /**
     * Save draft theme
     */
    @PutMapping("/draft")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'THEME_EDIT')")
    public ResponseEntity<ApiResponse<ThemeVersion>> saveDraft(
        @RequestBody Map<String, Object> payload,
        Authentication authentication
    ) {
        Long adminId = resolveAdminId(authentication);
        String note = (String) payload.getOrDefault("note", "Lưu bản nháp");
        Object themeData = payload.get("theme");

        try {
            String configJson = objectMapper.writeValueAsString(themeData != null ? themeData : payload);
            ThemeVersion draft = themeService.saveDraft(configJson, note, adminId);
            return ResponseEntity.ok(ApiResponse.success("Lưu bản nháp giao diện thành công!", draft));
        } catch (Exception e) {
            log.error("Không thể lưu draft theme: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Không thể lưu bản nháp: " + e.getMessage()));
        }
    }

    /**
     * Publish draft theme to the entire system
     */
    @PostMapping("/publish")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'THEME_PUBLISH')")
    public ResponseEntity<ApiResponse<ThemeVersion>> publishTheme(
        Authentication authentication,
        HttpServletRequest request
    ) {
        Long adminId = resolveAdminId(authentication);
        String ip = extractIp(request);
        String userAgent = request != null ? request.getHeader("User-Agent") : "Unknown";

        ThemeVersion published = themeService.publishCurrentDraft(adminId, ip, userAgent);
        return ResponseEntity.ok(ApiResponse.success("Xuất bản giao diện mới ra toàn hệ thống thành công!", published));
    }

    /**
     * Reset theme to original standard palette
     */
    @PostMapping("/reset")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'THEME_RESET')")
    public ResponseEntity<ApiResponse<ThemeVersion>> resetTheme(
        Authentication authentication,
        HttpServletRequest request
    ) {
        Long adminId = resolveAdminId(authentication);
        String ip = extractIp(request);
        String userAgent = request != null ? request.getHeader("User-Agent") : "Unknown";

        ThemeVersion reset = themeService.resetToDefault(adminId, ip, userAgent);
        return ResponseEntity.ok(ApiResponse.success("Đã khôi phục giao diện về bảng màu mặc định chuẩn!", reset));
    }

    /**
     * List all version history
     */
    @GetMapping("/versions")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'THEME_VIEW')")
    public ResponseEntity<ApiResponse<List<ThemeVersion>>> getVersions() {
        List<ThemeVersion> history = themeService.getVersionHistory();
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử phiên bản giao diện thành công", history));
    }

    /**
     * Rollback to a specific past version
     */
    @PostMapping("/versions/{id}/rollback")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'THEME_PUBLISH')")
    public ResponseEntity<ApiResponse<ThemeVersion>> rollback(
        @PathVariable Long id,
        Authentication authentication,
        HttpServletRequest request
    ) {
        Long adminId = resolveAdminId(authentication);
        String ip = extractIp(request);
        String userAgent = request != null ? request.getHeader("User-Agent") : "Unknown";

        ThemeVersion rolled = themeService.rollbackToVersion(id, adminId, ip, userAgent);
        return ResponseEntity.ok(ApiResponse.success("Rollback phiên bản giao diện thành công!", rolled));
    }
}
