package com.wayfare.modules.place.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.entity.Place;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.modules.place.service.SavedPlaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Danh sách "muốn đi" lưu TRÊN SERVER (bảng saved_places) — web và app mobile
 * cùng người dùng luôn thấy 1 danh sách duy nhất thay vì lưu rải rác theo
 * thiết bị. Cả 3 endpoint đều yêu cầu JWT (SecurityConfig: /api/users/**
 * theo cơ chế anyRequest().authenticated()).
 */
@RestController
@RequestMapping("/api/users/saved-places")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class SavedPlaceController {

    private final SavedPlaceService savedPlaceService;

    private String requireAuthEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new ResourceNotFoundException("Vui lòng đăng nhập để đồng bộ danh sách yêu thích");
        }
        return authentication.getName();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Place>>> getSavedPlaces(Authentication authentication) {
        String email = requireAuthEmail(authentication);
        List<Place> places = savedPlaceService.getSavedPlaces(email);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách địa điểm yêu thích thành công", places));
    }

    @PostMapping("/{placeId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addSavedPlace(
            @PathVariable Long placeId,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        savedPlaceService.addSavedPlace(email, placeId);
        return ResponseEntity.ok(ApiResponse.success("Đã lưu địa điểm vào danh sách yêu thích",
                Map.<String, Object>of("placeId", placeId, "saved", true)));
    }

    @DeleteMapping("/{placeId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeSavedPlace(
            @PathVariable Long placeId,
            Authentication authentication) {
        String email = requireAuthEmail(authentication);
        savedPlaceService.removeSavedPlace(email, placeId);
        return ResponseEntity.ok(ApiResponse.success("Đã bỏ địa điểm khỏi danh sách yêu thích",
                Map.<String, Object>of("placeId", placeId, "saved", false)));
    }
}
