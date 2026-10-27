package com.wayfare.modules.user.service;

import com.wayfare.modules.notification.service.NotificationService;
import com.wayfare.modules.community.service.PostService;

import com.wayfare.modules.user.dto.FollowUserDto;
import com.wayfare.modules.user.dto.UpdateProfileRequest;
import com.wayfare.modules.itinerary.dto.ItineraryDto;
import com.wayfare.modules.community.dto.PostDto;
import com.wayfare.modules.user.dto.UserProfileDto;
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

import com.wayfare.modules.user.dto.ChangePasswordRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    private final PasswordEncoder passwordEncoder;

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
    public List<FollowUserDto> getFollowingList(Long targetUserId, String currentUserEmail) {
        log.info("Getting following list for userId={}, viewer={}", targetUserId, currentUserEmail);
        User target = targetUserId != null
                ? userRepository.findById(targetUserId).orElseGet(() -> resolveUser(currentUserEmail))
                : resolveUser(currentUserEmail);
        User viewer = (currentUserEmail != null && !currentUserEmail.isBlank())
                ? userRepository.findByEmail(currentUserEmail).orElse(null)
                : null;

        List<UserFollow> follows = userFollowRepository.findByFollowerId(target.getId());
        return follows.stream().map(uf -> {
            User u = uf.getFollowing();
            boolean isFollowing = viewer != null && userFollowRepository.existsByFollowerIdAndFollowingId(viewer.getId(), u.getId());
            long fCount = userFollowRepository.countByFollowingId(u.getId());
            String role = u.getRoles() != null && u.getRoles().stream().anyMatch(r -> r.getName().contains("ADMIN"))
                    ? "Quản trị viên" : "Phượt thủ tự do";
            return FollowUserDto.builder()
                    .id(u.getId())
                    .fullName(u.getFullName())
                    .handle(u.getHandle() != null ? u.getHandle() : "@" + u.getEmail().split("@")[0])
                    .avatarUrl(u.getAvatarUrl() != null ? u.getAvatarUrl() : "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80")
                    .bio(u.getBio() != null ? u.getBio() : "Đam mê khám phá thiên nhiên và chia sẻ hành trình du lịch.")
                    .role(role)
                    .isFollowing(isFollowing)
                    .followersCount(fCount)
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FollowUserDto> getFollowersList(Long targetUserId, String currentUserEmail) {
        log.info("Getting followers list for userId={}, viewer={}", targetUserId, currentUserEmail);
        User target = targetUserId != null
                ? userRepository.findById(targetUserId).orElseGet(() -> resolveUser(currentUserEmail))
                : resolveUser(currentUserEmail);
        User viewer = (currentUserEmail != null && !currentUserEmail.isBlank())
                ? userRepository.findByEmail(currentUserEmail).orElse(null)
                : null;

        List<UserFollow> follows = userFollowRepository.findByFollowingId(target.getId());
        return follows.stream().map(uf -> {
            User u = uf.getFollower();
            boolean isFollowing = viewer != null && userFollowRepository.existsByFollowerIdAndFollowingId(viewer.getId(), u.getId());
            long fCount = userFollowRepository.countByFollowingId(u.getId());
            String role = u.getRoles() != null && u.getRoles().stream().anyMatch(r -> r.getName().contains("ADMIN"))
                    ? "Quản trị viên" : "Phượt thủ tự do";
            return FollowUserDto.builder()
                    .id(u.getId())
                    .fullName(u.getFullName())
                    .handle(u.getHandle() != null ? u.getHandle() : "@" + u.getEmail().split("@")[0])
                    .avatarUrl(u.getAvatarUrl() != null ? u.getAvatarUrl() : "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80")
                    .bio(u.getBio() != null ? u.getBio() : "Du khách trên nền tảng Wayfare.")
                    .role(role)
                    .isFollowing(isFollowing)
                    .followersCount(fCount)
                    .build();
        }).collect(Collectors.toList());
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
                        .creatorId(i.getCreator() != null ? i.getCreator().getId() : null)
                        .creatorName(i.getCreator() != null ? i.getCreator().getFullName() : "Wanderer")
                        .creatorEmail(i.getCreator() != null ? i.getCreator().getEmail() : "")
                        .creatorAvatar(i.getCreator() != null ? i.getCreator().getAvatarUrl() : null)
                        .title(i.getTitle())
                        .destination(i.getDestination())
                        .startDate(i.getStartDate())
                        .endDate(i.getEndDate())
                        .budgetTotal(i.getBudgetTotal())
                        .coverImageUrl(i.getCoverImageUrl())
                        .isAiGenerated(i.getIsAiGenerated())
                        .status(i.getStatus())
                        .createdAt(i.getCreatedAt())
                        .updatedAt(i.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());

        String rawTier = targetUser.getTier() != null ? targetUser.getTier() : "bronze";
        String rankTitle;
        if ("diamond".equalsIgnoreCase(rawTier)) {
            rankTitle = "Huyền Thoại Kim Cương";
        } else if ("gold".equalsIgnoreCase(rawTier)) {
            rankTitle = "Nhà Thám Hiểm Vàng";
        } else if ("silver".equalsIgnoreCase(rawTier)) {
            rankTitle = "Lữ Hành Bạc";
        } else {
            rankTitle = "Tân Thủ Khám Phá";
        }

        String roleStr = targetUser.getRoles() != null && targetUser.getRoles().stream().anyMatch(r -> r.getName().contains("ADMIN"))
                ? "Quản trị viên"
                : "Thành viên";

        String defaultCover = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1600&q=80";
        String defaultBio = "Đam mê khám phá thiên nhiên & trải nghiệm ẩm thực du lịch độc lạ cùng AI 🌍 ✈️";

        return UserProfileDto.builder()
                .id(targetUser.getId())
                .fullName(targetUser.getFullName())
                .handle(targetUser.getHandle() != null ? targetUser.getHandle() : "@" + targetUser.getEmail().split("@")[0])
                .email(targetUser.getEmail())
                .avatarUrl(targetUser.getAvatarUrl() != null ? targetUser.getAvatarUrl() : "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80")
                .coverImageUrl(defaultCover)
                .location(targetUser.getAddress() != null && !targetUser.getAddress().isBlank() ? targetUser.getAddress() : "Việt Nam")
                .address(targetUser.getAddress() != null ? targetUser.getAddress() : "")
                .phoneNumber(targetUser.getPhoneNumber() != null ? targetUser.getPhoneNumber() : "")
                .rank(rankTitle)
                .tier(rawTier.toLowerCase())
                .bio(targetUser.getBio() != null && !targetUser.getBio().isBlank() ? targetUser.getBio() : "")
                .travelStyle(targetUser.getTravelStyle() != null ? targetUser.getTravelStyle() : "Khám phá tự do")
                .budgetPreference(targetUser.getBudgetPreference() != null ? targetUser.getBudgetPreference() : "Linh hoạt")
                .isVerified(Boolean.TRUE.equals(targetUser.getIsVerified()))
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

    @Transactional
    public UserProfileDto updateUserProfile(Long userId, UpdateProfileRequest request, String currentUserEmail) {
        log.info("Updating user profile for userId={}, userEmail={}", userId, currentUserEmail);
        User currentUser = resolveUser(currentUserEmail);
        User targetUser = currentUser;
        if (userId != null && !currentUser.getId().equals(userId)) {
            boolean isAdmin = currentUser.getRoles() != null && currentUser.getRoles().stream().anyMatch(r -> r.getName().contains("ADMIN"));
            if (!isAdmin) {
                throw new RuntimeException("Bạn không có quyền chỉnh sửa hồ sơ của người khác!");
            }
            targetUser = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));
        }

        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            targetUser.setFullName(request.getFullName().trim());
        }
        if (request.getHandle() != null && !request.getHandle().trim().isEmpty()) {
            String cleanHandle = request.getHandle().trim();
            if (!cleanHandle.startsWith("@")) cleanHandle = "@" + cleanHandle;
            targetUser.setHandle(cleanHandle);
        }
        if (request.getBio() != null) {
            targetUser.setBio(request.getBio().trim());
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().trim().isEmpty()) {
            targetUser.setAvatarUrl(request.getAvatarUrl().trim());
        }
        if (request.getPhoneNumber() != null) {
            targetUser.setPhoneNumber(request.getPhoneNumber().trim());
        }
        if (request.getAddress() != null) {
            targetUser.setAddress(request.getAddress().trim());
        }
        if (request.getTravelStyle() != null && !request.getTravelStyle().trim().isEmpty()) {
            targetUser.setTravelStyle(request.getTravelStyle().trim());
        }
        if (request.getBudgetPreference() != null && !request.getBudgetPreference().trim().isEmpty()) {
            targetUser.setBudgetPreference(request.getBudgetPreference().trim());
        }

        userRepository.save(targetUser);
        log.info("Successfully updated profile for user ID {}", targetUser.getId());
        return getUserProfile(targetUser.getId(), targetUser.getEmail());
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        log.info("Processing password change request for user: {}", email);
        User user = resolveUser(email);

        if (request.getCurrentPassword() == null || !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            log.warn("Password change failed: Current password mismatch for user: {}", email);
            throw new IllegalArgumentException("Mật khẩu hiện tại không chính xác!");
        }

        if (request.getNewPassword() == null || !request.getNewPassword().equals(request.getConfirmPassword())) {
            log.warn("Password change failed: Confirm password mismatch for user: {}", email);
            throw new IllegalArgumentException("Mật khẩu xác nhận không trùng khớp!");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            log.warn("Password change failed: New password same as old password for user: {}", email);
            throw new IllegalArgumentException("Mật khẩu mới không được trùng với mật khẩu hiện tại!");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword().trim()));
        userRepository.save(user);
        log.info("Password successfully updated for user: {}", email);
    }

    public User resolveUser(String email) {
        if (email == null || email.isBlank()) {
            throw new ResourceNotFoundException("Vui lòng đăng nhập để thực hiện thao tác");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }
}


