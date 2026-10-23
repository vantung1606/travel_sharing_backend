package com.wayfare.modules.geo.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.modules.geo.dto.ProvinceDto;
import com.wayfare.modules.geo.service.ProvinceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Danh mục tỉnh thành & dấu chân du lịch.
 * GET /api/provinces                 — công khai cho mọi client (web/mobile) thay list hardcode.
 * GET /api/provinces/visited         — dấu chân của user đang đăng nhập (bảng user_provinces_visited).
 * PUT /api/provinces/{code}/visited — check-in / gỡ dấu chân (require auth).
 */
@RestController
@RequestMapping("/api/provinces")
@RequiredArgsConstructor
@Slf4j
public class ProvinceController {

    private final ProvinceService provinceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProvinceDto>>> getProvinces() {
        log.info("REST request to fetch province catalog");
        return ResponseEntity.ok(ApiResponse.success("Lấy danh mục tỉnh thành thành công",
                provinceService.listProvinces()));
    }

    @GetMapping("/visited")
    public ResponseEntity<ApiResponse<List<String>>> getVisitedProvinces(Authentication authentication) {
        log.info("REST request to fetch visited provinces for '{}'", authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Lấy dấu chân tỉnh thành thành công",
                provinceService.getVisitedProvinces(authentication.getName())));
    }

    @PutMapping("/{code}/visited")
    public ResponseEntity<ApiResponse<List<String>>> setVisitedProvinces(
            @PathVariable String code,
            @RequestParam(value = "visited", required = false) Boolean visitedParam,
            Authentication authentication) {
        boolean visited = visitedParam != null ? visitedParam : true;
        log.info("REST request to set visited={} for province '{}' by '{}'", visited, code, authentication.getName());
        List<String> updated = provinceService.setVisitedProvince(authentication.getName(), code, visited);
        if (visited) {
            return ResponseEntity.ok(ApiResponse.success("Đã lưu dấu chân tỉnh thành vào tài khoản ✓", updated));
        }
        return ResponseEntity.ok(ApiResponse.success("Đã gỡ dấu chân tỉnh thành khỏi tài khoản", updated));
    }
}
