package com.wayfare.service;

import com.wayfare.dto.RegisterRequest;
import com.wayfare.dto.UserDto;
import com.wayfare.entity.Role;
import com.wayfare.entity.User;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.repository.RoleRepository;
import com.wayfare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<UserDto> getUsers(String keyword, String role, String status) {
        log.info("Fetching users with keyword='{}', role='{}', status='{}'", keyword, role, status);

        String normalizedStatus = null;
        if (status != null && !status.equalsIgnoreCase("all") && !status.isBlank()) {
            normalizedStatus = status.equalsIgnoreCase("banned") || status.equalsIgnoreCase("locked") ? "LOCKED" : "ACTIVE";
        }

        String normalizedRole = null;
        if (role != null && !role.equalsIgnoreCase("all") && !role.isBlank()) {
            if (role.toLowerCase().contains("admin")) {
                normalizedRole = "ROLE_ADMIN";
            } else if (role.toLowerCase().contains("user")) {
                normalizedRole = "ROLE_USER";
            } else {
                normalizedRole = role;
            }
        }

        List<User> list = userRepository.searchUsers(
                keyword != null && !keyword.isBlank() ? keyword.trim() : null,
                normalizedStatus,
                normalizedRole
        );

        return list.stream().map(this::mapToDto).collect(Collectors.toList());
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

        Set<Role> roles = new HashSet<>(user.getRoles());
        if ("ROLE_ADMIN".equalsIgnoreCase(targetRole)) {
            roles.add(role);
        } else {
            // Revert to USER only
            roles.removeIf(r -> "ROLE_ADMIN".equalsIgnoreCase(r.getName()));
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

        String targetRole = (roleName != null && roleName.toUpperCase().contains("ADMIN")) ? "ROLE_ADMIN" : "ROLE_USER";
        Role role = roleRepository.findByName(targetRole)
                .orElseGet(() -> roleRepository.save(Role.builder().name(targetRole).description("Role").build()));

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").description("User Role").build()));

        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        if ("ROLE_ADMIN".equalsIgnoreCase(targetRole)) {
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

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalUsers", totalUsers);
        metrics.put("activeUsers", activeUsers);
        metrics.put("lockedUsers", lockedUsers);
        metrics.put("adminCount", adminCount);
        metrics.put("newUsersToday", Math.max(1, totalUsers / 10));
        return metrics;
    }

    private UserDto mapToDto(User u) {
        Set<String> roleNames = u.getRoles() != null
                ? u.getRoles().stream().map(Role::getName).collect(Collectors.toSet())
                : Set.of("ROLE_USER");

        boolean isBanned = "LOCKED".equalsIgnoreCase(u.getStatus()) || Boolean.TRUE.equals(u.getIsLocked());

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
                .tripsCount(roleNames.contains("ROLE_ADMIN") ? 18 : 6)
                .postsCount(roleNames.contains("ROLE_ADMIN") ? 32 : 12)
                .reviewsCount(roleNames.contains("ROLE_ADMIN") ? 45 : 8)
                .trustScore(isBanned ? 35 : (roleNames.contains("ROLE_ADMIN") ? 100 : 98))
                .riskScore(isBanned ? 86 : 4)
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }

    private String idForLog(Long id) {
        return id != null ? id.toString() : "null";
    }
}
