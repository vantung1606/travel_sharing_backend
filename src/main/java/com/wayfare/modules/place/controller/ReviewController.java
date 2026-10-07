package com.wayfare.modules.place.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.modules.place.dto.CreateReviewRequest;
import com.wayfare.modules.place.dto.ReviewDto;
import com.wayfare.modules.place.service.ReviewService;
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
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/{placeId}/reviews")
    public ResponseEntity<ApiResponse<List<ReviewDto>>> getReviewsByPlaceId(@PathVariable Long placeId) {
        log.info("REST request to get reviews for place ID: {}", placeId);
        List<ReviewDto> reviews = reviewService.getReviewsByPlaceId(placeId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đánh giá thành công", reviews));
    }

    @PostMapping("/{placeId}/reviews")
    public ResponseEntity<ApiResponse<ReviewDto>> addReview(
            @PathVariable Long placeId,
            @Valid @RequestBody CreateReviewRequest request,
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        log.info("REST request to add review for place ID: {} by user: {}", placeId, email);
        ReviewDto review = reviewService.addReview(placeId, request, email);
        return ResponseEntity.ok(ApiResponse.success("Gửi đánh giá thành công! Cảm ơn đóng góp của bạn.", review));
    }

    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable Long reviewId,
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        log.info("REST request to delete review ID: {} by user: {}", reviewId, email);
        reviewService.deleteReview(reviewId, email);
        return ResponseEntity.ok(ApiResponse.success("Xóa đánh giá thành công", null));
    }
}


