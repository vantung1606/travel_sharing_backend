package com.wayfare.modules.admin.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.entity.User;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.modules.admin.service.ActivityLogService;
import com.wayfare.modules.place.dto.ReviewDto;
import com.wayfare.modules.place.service.ReviewService;
import com.wayfare.repository.ReviewRepository;
import com.wayfare.repository.UserRepository;
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

import java.util.HashMap;
import java.util.Map;

/**
 * Kiểm duyệt ĐÁNH GIÁ địa điểm — mảnh từng còn thiếu hoàn toàn: 5110 review
 * thật trong hệ thống nhưng ADMIN/MODERATOR chưa có công cụ xem/ẩn/xóa.
 * Các review bị ẩn không còn tính vào điểm trung bình & lượt đánh giá.
 */
@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
@Slf4j
public class AdminReviewController {

    private final ReviewService reviewService;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    private User requireAdminUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new ResourceNotFoundException("Yêu cầu quyền Quản trị viên để thực hiện thao tác");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản quản trị không tồn tại"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ReviewDto>>> getReviews(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long placeId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false, defaultValue = "all") String visibility,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Boolean hidden = null;
        if ("hidden".equalsIgnoreCase(visibility)) {
            hidden = true;
        } else if ("visible".equalsIgnoreCase(visibility)) {
            hidden = false;
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReviewDto> result = reviewRepository
                .searchAllWithFilters(
                        keyword != null && keyword.isBlank() ? null : keyword,
                        placeId, rating, hidden, pageable)
                .map(reviewService::toDto);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đánh giá thành công", result));
    }

    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalReviews", reviewRepository.count());
        metrics.put("visibleReviews", reviewRepository.countByIsHiddenFalseOrNull());
        metrics.put("hiddenReviews", reviewRepository.countByIsHiddenTrue());
        return ResponseEntity.ok(ApiResponse.success("Thống kê đánh giá thành công", metrics));
    }

    /** Ẩn review khỏi trang công khai (vẫn giữ trong hệ thống để đối chiếu). */
    @PutMapping("/{id}/hide")
    public ResponseEntity<ApiResponse<ReviewDto>> hideReview(
            @PathVariable Long id, Authentication authentication) {
        User admin = requireAdminUser(authentication);
        log.info("Admin '{}' hiding review id: {}", admin.getEmail(), id);
        ReviewDto dto = reviewService.setReviewHidden(id, true);
        recordAudit(admin, "REVIEW_HIDE", "Ẩn đánh giá #" + id + " (địa điểm #" + dto.getPlaceId() + ")");
        return ResponseEntity.ok(ApiResponse.success("Đã ẩn đánh giá khỏi trang công khai", dto));
    }

    /** Hiện lại review đã bị ẩn. */
    @PutMapping("/{id}/unhide")
    public ResponseEntity<ApiResponse<ReviewDto>> unhideReview(
            @PathVariable Long id, Authentication authentication) {
        User admin = requireAdminUser(authentication);
        log.info("Admin '{}' unhiding review id: {}", admin.getEmail(), id);
        ReviewDto dto = reviewService.setReviewHidden(id, false);
        recordAudit(admin, "REVIEW_UNHIDE", "Hiện lại đánh giá #" + id + " (địa điểm #" + dto.getPlaceId() + ")");
        return ResponseEntity.ok(ApiResponse.success("Đã hiện lại đánh giá", dto));
    }

    /** Xóa vĩnh viễn review vi phạm — điểm địa điểm được tính lại. */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable Long id, Authentication authentication) {
        User admin = requireAdminUser(authentication);
        log.info("Admin '{}' deleting review id: {}", admin.getEmail(), id);
        ReviewDto snapshot = reviewService.toDto(reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Đánh giá không tồn tại với ID: " + id)));
        reviewService.deleteReviewByModerator(id);
        recordAudit(admin, "REVIEW_DELETE", "Xóa vĩnh viễn đánh giá #" + id + " (địa điểm #" + snapshot.getPlaceId() + ")");
        return ResponseEntity.ok(ApiResponse.success("Đã xóa vĩnh viễn đánh giá", null));
    }

    private void recordAudit(User admin, String action, String details) {
        try {
            activityLogService.recordLog(admin, action, details + " bởi " + admin.getEmail(),
                    "127.0.0.1", "Admin Portal");
        } catch (Exception e) {
            log.warn("Failed to record review moderation audit log: {}", e.getMessage());
        }
    }
}
