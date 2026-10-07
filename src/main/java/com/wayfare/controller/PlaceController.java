package com.wayfare.controller;

import com.wayfare.dto.ApiResponse;
import com.wayfare.dto.CreatePlaceRequest;
import com.wayfare.dto.PlaceDto;
import com.wayfare.service.PlaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class PlaceController {

    private final PlaceService placeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PlaceDto>>> getAllPlaces(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
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
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        log.info("REST request to create new place '{}' by email: {}", request.getName(), email);
        PlaceDto created = placeService.createPlace(request, email);
        return ResponseEntity.ok(ApiResponse.success("Đăng tải địa điểm mới thành công!", created));
    }

    @GetMapping("/my-places")
    public ResponseEntity<ApiResponse<List<PlaceDto>>> getMyPlaces(
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        log.info("REST request to get places created by: {}", email);
        List<PlaceDto> myPlaces = placeService.getMyPlaces(email);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách địa điểm của bạn thành công", myPlaces));
    }

    @GetMapping("/admin/pending")
    public ResponseEntity<ApiResponse<List<PlaceDto>>> getPendingPlaces() {
        log.info("REST request to get all pending places for admin moderation");
        List<PlaceDto> pending = placeService.getPendingPlaces();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách địa điểm chờ duyệt thành công", pending));
    }

    @PutMapping("/admin/{id}/approve")
    public ResponseEntity<ApiResponse<PlaceDto>> approvePlace(@PathVariable Long id) {
        log.info("REST request to approve place ID: {}", id);
        PlaceDto approved = placeService.approvePlace(id);
        return ResponseEntity.ok(ApiResponse.success("Phê duyệt địa điểm thành công!", approved));
    }

    @PutMapping("/admin/{id}/reject")
    public ResponseEntity<ApiResponse<PlaceDto>> rejectPlace(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Thông tin chưa đầy đủ hoặc không hợp lệ") String reason) {
        log.info("REST request to reject place ID: {} with reason: {}", id, reason);
        PlaceDto rejected = placeService.rejectPlace(id, reason);
        return ResponseEntity.ok(ApiResponse.success("Từ chối địa điểm thành công!", rejected));
    }

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
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        log.info("REST request to delete place ID {} by {}", id, email);
        placeService.deletePlace(id, email);
        return ResponseEntity.ok(ApiResponse.success("Xóa địa điểm thành công!", null));
    }
}
