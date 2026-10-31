package com.wayfare.modules.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayfare.common.dto.ApiResponse;
import com.wayfare.entity.ThemeVersion;
import com.wayfare.modules.admin.service.ThemeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class PublicSiteConfigController {

    private final ThemeService themeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Public endpoint to fetch active branding and design tokens
     * Used by Web Frontend and Mobile App on initialization
     */
    @GetMapping("/site-config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPublicSiteConfig() {
        ThemeVersion published = themeService.getPublishedTheme();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> configMap = objectMapper.readValue(published.getConfigJson(), Map.class);
            configMap.put("version", published.getVersionNo());
            configMap.put("publishedAt", published.getPublishedAt());
            return ResponseEntity.ok(ApiResponse.success("Lấy cấu hình giao diện thành công", configMap));
        } catch (Exception e) {
            log.error("Lỗi khi parse theme config JSON: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.success("Dùng giao diện mặc định", Map.of()));
        }
    }
}
