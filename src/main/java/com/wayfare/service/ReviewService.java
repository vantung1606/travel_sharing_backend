package com.wayfare.service;

import com.wayfare.dto.CreateReviewRequest;
import com.wayfare.dto.ReviewDto;
import com.wayfare.entity.Place;
import com.wayfare.entity.Review;
import com.wayfare.entity.User;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.repository.PlaceRepository;
import com.wayfare.repository.ReviewRepository;
import com.wayfare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ReviewDto> getReviewsByPlaceId(Long placeId) {
        log.info("Fetching reviews for place ID: {}", placeId);
        return reviewRepository.findByPlaceIdOrderByCreatedAtDesc(placeId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ReviewDto addReview(Long placeId, CreateReviewRequest request, String userEmail) {
        log.info("User '{}' is submitting a review for place ID: {}", userEmail, placeId);

        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new ResourceNotFoundException("Địa điểm không tồn tại với ID: " + placeId));

        User author = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với email: " + userEmail));

        Review review = Review.builder()
                .place(place)
                .author(author)
                .rating(request.getRating())
                .comment(request.getComment().trim())
                .build();

        Review savedReview = reviewRepository.save(review);
        log.info("Saved review ID: {} for place ID: {}", savedReview.getId(), placeId);

        // Recalculate average rating and review count
        recalculatePlaceRating(place);

        return toDto(savedReview);
    }

    @Transactional
    public void deleteReview(Long reviewId, String userEmail) {
        log.info("Request to delete review ID: {} by user: {}", reviewId, userEmail);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Đánh giá không tồn tại với ID: " + reviewId));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        boolean isAdmin = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> r.getName().equalsIgnoreCase("ROLE_ADMIN"));

        boolean isAuthor = review.getAuthor() != null && review.getAuthor().getId().equals(user.getId());

        if (!isAdmin && !isAuthor) {
            throw new IllegalArgumentException("Bạn không có quyền xóa đánh giá này");
        }

        Place place = review.getPlace();
        reviewRepository.delete(review);
        log.info("Deleted review ID: {}", reviewId);

        if (place != null) {
            recalculatePlaceRating(place);
        }
    }

    private void recalculatePlaceRating(Place place) {
        Double avg = reviewRepository.calculateAverageRating(place.getId());
        Long count = reviewRepository.countByPlaceId(place.getId());

        if (avg != null && count != null && count > 0) {
            BigDecimal roundedAvg = BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP);
            place.setAverageRating(roundedAvg);
            place.setReviewCount(count.intValue());
        } else {
            place.setAverageRating(BigDecimal.ZERO);
            place.setReviewCount(0);
        }

        placeRepository.save(place);
        log.info("Recalculated place ID: {} -> averageRating: {}, reviewCount: {}",
                place.getId(), place.getAverageRating(), place.getReviewCount());
    }

    public ReviewDto toDto(Review review) {
        if (review == null) return null;

        User author = review.getAuthor();
        Place place = review.getPlace();

        return ReviewDto.builder()
                .id(review.getId())
                .placeId(place != null ? place.getId() : null)
                .placeName(place != null ? place.getName() : null)
                .authorId(author != null ? author.getId() : null)
                .authorName(author != null ? author.getFullName() : "Du khách Wayfare")
                .authorHandle(author != null ? author.getHandle() : "@traveler")
                .authorAvatar(author != null ? author.getAvatarUrl() : null)
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
