package com.wayfare.modules.place.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.modules.place.dto.CreatePlaceRequest;
import com.wayfare.modules.place.dto.PlaceDto;
import com.wayfare.modules.place.service.PlaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/places")
@CrossOrigin(origins = "*")
@Slf4j
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    private String requireAuthEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new ResourceNotFoundException("Vui lòng đăng nhập để thực hiện thao tác");
        }
        return authentication.getName();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<?>> getAllPlaces(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        // Phân trang là opt-in: chỉ khi client gửi page/size mới trả PagedResponse,
        // ngược lại giữ nguyên shape cũ (List) để không phá vỡ các màn hình đang dùng.
        if (page != null || size != null) {
            int safePage = page != null ? Math.max(page, 0) : 0;
            int safeSize = size != null ? size : 24;
            log.info("REST request to get paged places: page={}, size={}, city={}, category={}, status={}, keyword={}",
                    safePage, safeSize, city, category, status, keyword);
            return ResponseEntity.ok(ApiResponse.success("Lấy danh sách địa điểm thành công",
                    placeService.getPlacesPaged(city, category, status, keyword, safePage, safeSize)));
        }

        log.info("REST request to get places: city={}, category={}, status={}, keyword={}", city, category, status, keyword);
        List<PlaceDto> places = placeService.getAllPlaces(city, category, status, keyword);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách địa điểm thành công", places));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PlaceDto>> getPlaceById(@PathVariable Long id) {
        log.info("REST request to get place detail: id={}", id);
        PlaceDto place = placeService.getPlaceById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết địa điểm thành công", place));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PlaceDto>> createPlace(
            @Valid @RequestBody CreatePlaceRequest request,
            Authentication authentication) {
        String userEmail = requireAuthEmail(authentication);
        log.info("REST request to create new place '{}' by email: {}", request.getName(), userEmail);
        PlaceDto created = placeService.createPlace(request, userEmail);
        return ResponseEntity.ok(ApiResponse.success("Đăng tải địa điểm mới thành công!", created));
    }

    @GetMapping("/my-places")
    public ResponseEntity<ApiResponse<List<PlaceDto>>> getMyPlaces(Authentication authentication) {
        String userEmail = requireAuthEmail(authentication);
        log.info("REST request to get places created by: {}", userEmail);
        List<PlaceDto> myPlaces = placeService.getMyPlaces(userEmail);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách địa điểm của bạn thành công", myPlaces));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    @GetMapping({"/admin/pending", "/pending"})
    public ResponseEntity<ApiResponse<List<PlaceDto>>> getPendingPlaces() {
        log.info("REST request to get all pending places for admin moderation");
        List<PlaceDto> pending = placeService.getPendingPlaces();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách địa điểm chờ duyệt thành công", pending));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    @PostMapping("/admin/geocode-missing")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> geocodeMissingPlaces() {
        log.info("REST request to geocode all places missing GPS coordinates");
        int count = placeService.geocodeAllMissingPlaces();
        return ResponseEntity.ok(ApiResponse.success("Đã đồng bộ tọa độ GPS cho " + count + " địa điểm thành công!", java.util.Map.of("fixedCount", count)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    @PutMapping("/admin/{id}/approve")
    public ResponseEntity<ApiResponse<PlaceDto>> approvePlace(@PathVariable Long id) {
        log.info("REST request to approve place ID: {}", id);
        PlaceDto approved = placeService.approvePlace(id);
        return ResponseEntity.ok(ApiResponse.success("Phê duyệt địa điểm thành công!", approved));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    @PutMapping("/admin/{id}/reject")
    public ResponseEntity<ApiResponse<PlaceDto>> rejectPlace(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Thông tin chưa đầy đủ hoặc không hợp lệ") String reason) {
        log.info("REST request to reject place ID: {} with reason: {}", id, reason);
        PlaceDto rejected = placeService.rejectPlace(id, reason);
        return ResponseEntity.ok(ApiResponse.success("Từ chối địa điểm thành công!", rejected));
    }

    /**
     * Xác thực / bỏ xác thực địa điểm (quy trình Badge "Đã xác thực" — trước
     * đây cờ này chỉ được set ẩn trong DataInitializer, admin không có công cụ).
     */
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    @PutMapping("/admin/{id}/verified")
    public ResponseEntity<ApiResponse<PlaceDto>> togglePlaceVerified(
            @PathVariable Long id,
            @RequestParam boolean verified,
            Authentication authentication) {
        log.info("REST request to set verified={} for place ID: {} by {}", verified, id, authentication.getName());
        PlaceDto updated = placeService.setVerified(id, verified, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(
                verified ? "Đã cấp badge Xác thực cho địa điểm!" : "Đã gỡ badge Xác thực của địa điểm!", updated));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<PlaceDto>> updatePlaceStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        log.info("REST request to update place ID {} status to {}", id, status);
        PlaceDto updated = placeService.updatePlaceStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái địa điểm thành công!", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePlace(
            @PathVariable Long id,
            Authentication authentication) {
        String userEmail = requireAuthEmail(authentication);
        log.info("REST request to delete place ID {} by {}", id, userEmail);
        placeService.deletePlace(id, userEmail);
        return ResponseEntity.ok(ApiResponse.success("Xóa địa điểm thành công!", null));
    }
}
