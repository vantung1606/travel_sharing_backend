package com.wayfare.service;

import com.wayfare.dto.ItineraryDto;
import com.wayfare.dto.PostDto;
import com.wayfare.dto.UserProfileDto;
import com.wayfare.entity.Itinerary;
import com.wayfare.entity.Post;
import com.wayfare.entity.User;
import com.wayfare.entity.UserFollow;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.repository.ItineraryRepository;
import com.wayfare.repository.PostRepository;
import com.wayfare.repository.UserFollowRepository;
import com.wayfare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserFollowService {

    private final UserRepository userRepository;
    private final UserFollowRepository userFollowRepository;
    private final PostRepository postRepository;
    private final ItineraryRepository itineraryRepository;
    private final NotificationService notificationService;
    private final PostService postService;

    @Transactional
    public Map<String, Object> toggleFollow(Long targetUserId, String currentUserEmail) {
        log.info("Toggle follow targetUserId={} by userEmail={}", targetUserId, currentUserEmail);

        User currentUser = resolveUser(currentUserEmail);
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + targetUserId));

        if (currentUser.getId().equals(targetUser.getId())) {
            throw new RuntimeException("Bạn không thể tự theo dõi chính mình!");
        }

        Optional<UserFollow> existing = userFollowRepository.findByFollowerIdAndFollowingId(currentUser.getId(), targetUser.getId());
        boolean isFollowing;

        if (existing.isPresent()) {
            userFollowRepository.delete(existing.get());
            isFollowing = false;
            log.info("User {} unfollowed user {}", currentUser.getEmail(), targetUser.getEmail());
        } else {
            UserFollow newFollow = UserFollow.builder()
                    .follower(currentUser)
                    .following(targetUser)
                    .build();
            userFollowRepository.save(newFollow);
            isFollowing = true;
            log.info("User {} followed user {}", currentUser.getEmail(), targetUser.getEmail());

            // Send notification
            try {
                notificationService.sendNotification(
                        targetUser,
                        currentUser,
                        "FOLLOW",
                        currentUser.getFullName() + " đã bắt đầu theo dõi bạn trên Wayfare!",
                        "/community"
                );
            } catch (Exception e) {
                log.warn("Failed to send follow notification: {}", e.getMessage());
            }
        }

        long followersCount = userFollowRepository.countByFollowingId(targetUser.getId());
        long followingCount = userFollowRepository.countByFollowerId(targetUser.getId());

        return Map.of(
                "isFollowing", isFollowing,
                "followersCount", followersCount,
                "followingCount", followingCount
        );
    }

    @Transactional(readOnly = true)
    public List<Long> getFollowingUserIds(String currentUserEmail) {
        log.info("Getting following user IDs for {}", currentUserEmail);
        User currentUser = resolveUser(currentUserEmail);
        return userFollowRepository.findByFollowerId(currentUser.getId()).stream()
                .map(uf -> uf.getFollowing().getId())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserProfileDto getUserProfile(Long targetUserId, String currentUserEmail) {
        log.info("Getting user profile for targetUserId={}, viewer={}", targetUserId, currentUserEmail);

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + targetUserId));

        User currentUser = null;
        if (currentUserEmail != null && !currentUserEmail.isBlank()) {
            currentUser = userRepository.findByEmail(currentUserEmail).orElse(null);
        }

        boolean isFollowing = false;
        boolean isOwnProfile = currentUser != null && currentUser.getId().equals(targetUser.getId());

        if (currentUser != null && !isOwnProfile) {
            isFollowing = userFollowRepository.existsByFollowerIdAndFollowingId(currentUser.getId(), targetUser.getId());
        }

        long followersCount = userFollowRepository.countByFollowingId(targetUser.getId());
        long followingCount = userFollowRepository.countByFollowerId(targetUser.getId());

        // Get Posts
        List<Post> allUserPosts = postRepository.findByAuthorOrderByCreatedAtDesc(targetUser);
        List<PostDto> postDtos = allUserPosts.stream()
                .filter(p -> {
                    if (isOwnProfile) {
                        return true; // Owner sees all their posts (ACTIVE, PENDING_REVIEW, PRIVATE, PUBLIC)
                    }
                    // Others only see ACTIVE and PUBLIC
                    return "ACTIVE".equals(p.getStatus()) && !"PRIVATE".equalsIgnoreCase(p.getVisibility());
                })
                .map(p -> postService.mapToDto(p, isOwnProfile ? targetUser : null))
                .collect(Collectors.toList());

        // Get Itineraries
        List<Itinerary> userItineraries = itineraryRepository.findByCreatorOrderByCreatedAtDesc(targetUser);
        List<ItineraryDto> itinDtos = userItineraries.stream()
                .filter(i -> isOwnProfile || "ACTIVE".equalsIgnoreCase(i.getStatus()))
                .map(i -> ItineraryDto.builder()
                        .id(i.getId())
                        .title(i.getTitle())
                        .destination(i.getDestination())
                        .coverImageUrl(i.getCoverImageUrl())
                        .budgetTotal(i.getBudgetTotal())
                        .isAiGenerated(i.getIsAiGenerated())
                        .status(i.getStatus())
                        .createdAt(i.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        String roleStr = targetUser.getRoles() != null && targetUser.getRoles().stream().anyMatch(r -> r.getName().contains("ADMIN"))
                ? "Quản trị viên"
                : "Phượt thủ tự do";

        return UserProfileDto.builder()
                .id(targetUser.getId())
                .fullName(targetUser.getFullName())
                .handle(targetUser.getHandle() != null ? targetUser.getHandle() : "@" + targetUser.getEmail().split("@")[0])
                .email(isOwnProfile ? targetUser.getEmail() : null)
                .avatarUrl(targetUser.getAvatarUrl() != null ? targetUser.getAvatarUrl() : "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80")
                .bio(targetUser.getBio() != null ? targetUser.getBio() : "Đam mê xê dịch, khám phá thiên nhiên và chia sẻ hành trình du lịch khắp Việt Nam.")
                .travelStyle(targetUser.getTravelStyle() != null ? targetUser.getTravelStyle() : "Phượt bụi & Khám phá")
                .budgetPreference(targetUser.getBudgetPreference() != null ? targetUser.getBudgetPreference() : "Tiết kiệm / Hợp lý")
                .isVerified(targetUser.getIsVerified() != null ? targetUser.getIsVerified() : true)
                .role(roleStr)
                .followersCount(followersCount)
                .followingCount(followingCount)
                .postsCount(postDtos.size())
                .itinerariesCount(itinDtos.size())
                .isFollowing(isFollowing)
                .posts(postDtos)
                .itineraries(itinDtos)
                .build();
    }

    private User resolveUser(String email) {
        if (email == null || email.isBlank()) {
            return userRepository.findByEmail("tung@gmail.com")
                    .orElseGet(() -> userRepository.findAll().stream().findFirst()
                            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng mặc định")));
        }
        return userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.findByEmail("tung@gmail.com")
                        .orElseGet(() -> userRepository.findAll().stream().findFirst()
                                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email))));
    }
}
