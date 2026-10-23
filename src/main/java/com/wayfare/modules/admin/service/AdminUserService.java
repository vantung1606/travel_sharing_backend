package com.wayfare.modules.admin.service;

import com.wayfare.modules.notification.service.NotificationService;

import com.wayfare.modules.auth.dto.RegisterRequest;
import com.wayfare.modules.user.dto.UserDto;
import com.wayfare.entity.Role;
import com.wayfare.entity.User;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.repository.ItineraryRepository;
import com.wayfare.repository.PostRepository;
import com.wayfare.repository.ReviewRepository;
import com.wayfare.repository.RoleRepository;
import com.wayfare.repository.SavedPlaceRepository;
import com.wayfare.repository.UserFollowRepository;
import com.wayfare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PostRepository postRepository;
    private final ItineraryRepository itineraryRepository;
    private final ReviewRepository reviewRepository;
    private final SavedPlaceRepository savedPlaceRepository;
    private final UserFollowRepository userFollowRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public Page<UserDto> getUsers(String keyword, String role, String status, Pageable pageable) {
        String normalizedStatus = null;
        if (status != null && !status.equalsIgnoreCase("all") && !status.isBlank()) {
            normalizedStatus = status.equalsIgnoreCase("banned") || status.equalsIgnoreCase("locked") ? "LOCKED" : "ACTIVE";
        }

        String normalizedRole = null;
        if (role != null && !role.equalsIgnoreCase("all") && !role.isBlank()) {
            if (role.toLowerCase().contains("admin")) {
                normalizedRole = "ROLE_ADMIN";
            } else if (role.toLowerCase().contains("moderator")) {
                normalizedRole = "ROLE_MODERATOR";
            } else if (role.toLowerCase().contains("user")) {
                normalizedRole = "ROLE_USER";
            } else {
                normalizedRole = role;
            }
        }

        Page<User> result = userRepository.searchUsersPage(                keyword != null && !keyword.isBlank() ? keyword.trim() : null,
                normalizedStatus,
                normalizedRole,
                pageable);

        return result.map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public UserDto getUserById(Long id) {
        log.info("Fetching user detail for id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return mapToDto(user);
    }

    @Transactional
    public UserDto toggleUserStatus(Long userId, String adminEmail) {
        log.info("Admin '{}' toggling status for user id: {}", adminEmail, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + idForLog(userId)));

        boolean currentlyLocked = "LOCKED".equalsIgnoreCase(user.getStatus()) || Boolean.TRUE.equals(user.getIsLocked());
        if (currentlyLocked) {
            user.setStatus("ACTIVE");
            user.setIsLocked(false);
            log.info("User id: {} unlocked successfully.", userId);
        } else {
            user.setStatus("LOCKED");
            user.setIsLocked(true);
            log.info("User id: {} locked/banned successfully.", userId);
        }

        User saved = userRepository.save(user);
        String actionType = currentlyLocked ? "USER_UNLOCK" : "USER_LOCK";
        String details = (currentlyLocked ? "Mở khóa tài khoản: " : "Khóa tài khoản: ") + user.getEmail() + " bởi Admin " + adminEmail;
        activityLogService.recordLog(user, actionType, details, "127.0.0.1", "Admin Portal");

        // Send real notification to the affected user
        try {
            notificationService.sendNotification(
                    user,
                    null,
                    "SYSTEM",
                    currentlyLocked ? "Tài khoản của bạn đã được Quản trị viên mở khóa thành công." : "Tài khoản của bạn đã bị tạm khóa bởi Quản trị viên do vi phạm quy định.",
                    "/profile"
            );
        } catch (Exception e) {
            log.warn("Failed to send lock status notification: {}", e.getMessage());
        }

        return mapToDto(saved);
    }

    @Transactional
    public UserDto updateUserRole(Long userId, String roleName, String adminEmail) {
        log.info("Admin '{}' updating role to '{}' for user id: {}", adminEmail, roleName, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        String targetRole = roleName.toUpperCase();
        if (!targetRole.startsWith("ROLE_")) {
            targetRole = "ROLE_" + targetRole;
        }

        Role role = roleRepository.findByName(targetRole)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name(roleName.toUpperCase().startsWith("ROLE_") ? roleName.toUpperCase() : "ROLE_" + roleName.toUpperCase())
                        .description("Vai trò " + roleName)
                        .build()));

        Set<Role> roles = new HashSet<>(user.getRoles() != null ? user.getRoles() : Set.of());
        if ("ROLE_ADMIN".equalsIgnoreCase(targetRole)) {
            roles.add(role);
            roles.removeIf(r -> "ROLE_MODERATOR".equalsIgnoreCase(r.getName()));
        } else if ("ROLE_MODERATOR".equalsIgnoreCase(targetRole)) {
            // Moderator giữ quyền kiểm duyệt (bài viết / đánh giá / địa điểm)
            // nhưng KHÔNG có quyền quản trị hệ thống (users, stats, audit logs).
            roles.add(role);
            roles.removeIf(r -> "ROLE_ADMIN".equalsIgnoreCase(r.getName()));
        } else {
            // Hạ về USER thường: gỡ cả ADMIN lẫn MODERATOR
            roles.removeIf(r -> "ROLE_ADMIN".equalsIgnoreCase(r.getName())
                    || "ROLE_MODERATOR".equalsIgnoreCase(r.getName()));
            Role userRole = roleRepository.findByName("ROLE_USER").orElse(role);
            roles.add(userRole);
        }

        user.setRoles(roles);
        User saved = userRepository.save(user);
        log.info("Successfully updated roles for user id: {} -> {}", userId, roles.stream().map(Role::getName).collect(Collectors.joining(", ")));
        activityLogService.recordLog(user, "ROLE_MANAGEMENT", "Cập nhật phân quyền tài khoản " + user.getEmail() + " thành " + targetRole + " bởi Admin " + adminEmail, "127.0.0.1", "Admin Portal");

        // Send real notification to user
        try {
            notificationService.sendNotification(
                    user,
                    null,
                    "SYSTEM",
                    "Phân quyền tài khoản của bạn đã được nâng cấp lên vai trò: " + targetRole,
                    "/profile"
            );
        } catch (Exception e) {
            log.warn("Failed to send role update notification: {}", e.getMessage());
        }

        return mapToDto(saved);
    }

    @Transactional
    public UserDto createUser(RegisterRequest request, String roleName) {
        log.info("Creating new user manually: email='{}', role='{}'", request.getEmail(), roleName);
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email '" + request.getEmail() + "' đã tồn tại trong hệ thống.");
        }

        String handle = request.getHandle();
        if (handle == null || handle.isBlank()) {
            handle = "@" + request.getEmail().split("@")[0].toLowerCase().replaceAll("[^a-z0-9_]", "");
        }

        String targetRole;
        if (roleName != null && roleName.toUpperCase().contains("ADMIN")) {
            targetRole = "ROLE_ADMIN";
        } else if (roleName != null && roleName.toUpperCase().contains("MODERATOR")) {
            targetRole = "ROLE_MODERATOR";
        } else {
            targetRole = "ROLE_USER";
        }
        final String roleDescription;
        if ("ROLE_MODERATOR".equals(targetRole)) {
            roleDescription = "Kiểm duyệt viên nội dung";
        } else if ("ROLE_USER".equals(targetRole)) {
            roleDescription = "Người dùng tiêu chuẩn";
        } else {
            roleDescription = "Quản trị viên hệ thống";
        }
        Role role = roleRepository.findByName(targetRole)
                .orElseGet(() -> roleRepository.save(Role.builder().name(targetRole).description(roleDescription).build()));

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").description("Người dùng tiêu chuẩn").build()));

        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        if (!"ROLE_USER".equalsIgnoreCase(targetRole)) {
            roles.add(role);
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword() != null ? request.getPassword() : "123456"))
                .fullName(request.getFullName() != null ? request.getFullName() : request.getEmail().split("@")[0])
                .handle(handle)
                .phoneNumber(request.getPhoneNumber())
                .avatarUrl("https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80")
                .status("ACTIVE")
                .isLocked(false)
                .isVerified(true)
                .roles(roles)
                .build();

        User saved = userRepository.save(user);
        log.info("Successfully created user id: {}", saved.getId());
        return mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getUserMetrics() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus("ACTIVE");
        long lockedUsers = userRepository.countByStatus("LOCKED");
        long adminCount = userRepository.countByRoleName("ROLE_ADMIN");
        long moderatorCount = userRepository.countByRoleName("ROLE_MODERATOR");
        // Số liệu THẬT: tài khoản tạo trong hôm nay (trước đây là totalUsers/10 — số ảo)
        long newUsersToday = userRepository.countByCreatedAtAfter(java.time.LocalDate.now().atStartOfDay());

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalUsers", totalUsers);
        metrics.put("activeUsers", activeUsers);
        metrics.put("lockedUsers", lockedUsers);
        metrics.put("adminCount", adminCount);
        metrics.put("moderatorCount", moderatorCount);
        metrics.put("newUsersToday", newUsersToday);
        return metrics;
    }

    /**
     * Cấp / gỡ badge "Xác thực" cho người dùng (trước đây cờ này chỉ set
     * ẩn trong DataInitializer — không có công cụ quản trị).
     */
    @Transactional
    public UserDto setUserVerified(Long userId, boolean verified, String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + idForLog(userId)));
        user.setIsVerified(verified);
        User saved = userRepository.save(user);
        activityLogService.recordLog(user, verified ? "USER_VERIFY" : "USER_UNVERIFY",
                (verified ? "Cấp badge Xác thực cho tài khoản: " : "Gỡ badge Xác thực của tài khoản: ")
                        + user.getEmail() + " bởi Admin " + adminEmail,
                "127.0.0.1", "Admin Portal");
        return mapToDto(saved);
    }

    /**
     * Đặt lại mật khẩu cho người dùng: mật khẩu tạm ngẫu nhiên được trả về
     * MỘT LẦN cho admin gửi cho người dùng (không lưu dạng plaintext trong DB),
     * đồng thời ghi audit log + gửi notification trong app.
     */
    @Transactional
    public Map<String, Object> resetPassword(Long userId, String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + idForLog(userId)));

        String tempPassword = generateTempPassword();
        user.setPassword(passwordEncoder.encode(tempPassword));
        userRepository.save(user);
        log.info("Admin '{}' reset password for user id: {}", adminEmail, userId);
        activityLogService.recordLog(user, "USER_RESET_PASSWORD",
                "Quản trị viên " + adminEmail + " đặt lại mật khẩu cho tài khoản " + user.getEmail(),
                "127.0.0.1", "Admin Portal");

        try {
            notificationService.sendNotification(user, null, "SYSTEM",
                    "Mật khẩu tài khoản của bạn đã được Quản trị viên đặt lại. Vui lòng liên hệ quản trị để nhận mật khẩu tạm và đổi lại ngay.",
                    "/profile");
        } catch (Exception e) {
            log.warn("Failed to send reset-password notification: {}", e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("email", user.getEmail());
        result.put("tempPassword", tempPassword);
        return result;
    }

    /**
     * Thống kê hoạt động THẬT của 1 người dùng (gọi từ trang chi tiết admin):
     * bài viết, lịch trình, đánh giá, địa điểm lưu, follower/following.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUserActivitySummary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + idForLog(userId)));
        Map<String, Object> summary = new HashMap<>();
        summary.put("posts", postRepository.findByAuthorOrderByCreatedAtDesc(user).size());
        summary.put("trips", itineraryRepository.findByCreatorOrderByCreatedAtDesc(user).size());
        summary.put("reviews", reviewRepository.countByAuthorId(userId));
        summary.put("savedPlaces", savedPlaceRepository.countByUserId(userId));
        summary.put("followers", userFollowRepository.countByFollowingId(userId));
        summary.put("following", userFollowRepository.countByFollowerId(userId));
        return summary;
    }

    private UserDto mapToDto(User u) {
        Set<String> roleNames = u.getRoles() != null
                ? u.getRoles().stream().map(Role::getName).collect(Collectors.toSet())
                : Set.of("ROLE_USER");

        boolean isBanned = "LOCKED".equalsIgnoreCase(u.getStatus()) || Boolean.TRUE.equals(u.getIsLocked());

        int realPostsCount = 0;
        try {
            realPostsCount = postRepository.findByAuthorOrderByCreatedAtDesc(u).size();
        } catch (Exception e) {
            log.warn("Could not calculate realPostsCount for user {}: {}", u.getId(), e.getMessage());
        }

        int realTripsCount = 0;
        try {
            realTripsCount = itineraryRepository.findByCreatorOrderByCreatedAtDesc(u).size();
        } catch (Exception e) {
            log.warn("Could not calculate realTripsCount for user {}: {}", u.getId(), e.getMessage());
        }

        String resolvedTier = u.getTier() != null && !u.getTier().isBlank() ? u.getTier().toLowerCase() : "bronze";
        if (roleNames.contains("ROLE_ADMIN") && ("bronze".equalsIgnoreCase(resolvedTier) || resolvedTier.isBlank())) {
            resolvedTier = "diamond";
        }

        return UserDto.builder()
                .id(u.getId())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .handle(u.getHandle() != null ? u.getHandle() : "@" + u.getEmail().split("@")[0])
                .avatarUrl(u.getAvatarUrl() != null ? u.getAvatarUrl() : "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80")
                .phoneNumber(u.getPhoneNumber())
                .bio(u.getBio())
                .travelStyle(u.getTravelStyle())
                .budgetPreference(u.getBudgetPreference())
                .isVerified(Boolean.TRUE.equals(u.getIsVerified()))
                .status(isBanned ? "LOCKED" : "ACTIVE")
                .isLocked(isBanned)
                .roles(roleNames)
                .tier(resolvedTier)
                .tripsCount(realTripsCount)
                .postsCount(realPostsCount)
                // Số liệu THẬT từ bảng reviews (trước đây luôn hardcode 0)
                .reviewsCount((int) reviewRepository.countByAuthorId(u.getId()))
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }

    private String generateTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String idForLog(Long id) {
        return id != null ? id.toString() : "null";
    }
}


