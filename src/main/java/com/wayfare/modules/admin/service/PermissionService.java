package com.wayfare.modules.admin.service;

import com.wayfare.entity.*;
import com.wayfare.modules.admin.dto.*;
import com.wayfare.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    // Fast memory cache for effective permissions (email -> CacheEntry)
    private final Map<String, CacheEntry> permissionCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000; // 5 minutes

    private record CacheEntry(Set<String> permissions, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MILLIS;
        }
    }

    public void clearUserCache(String email) {
        if (email != null) {
            permissionCache.remove(email);
        }
    }

    public void clearAllCache() {
        permissionCache.clear();
    }

    /**
     * Seeds initial standard permissions and role assignments if not yet present
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initDefaultPermissions() {
        log.info("Khởi tạo danh mục quyền RBAC hệ thống (Database-driven Dynamic RBAC)...");

        // 1. Define standard catalog
        List<PermissionSpec> specs = List.of(
            // DASHBOARD
            new PermissionSpec("DASHBOARD_VIEW", "Xem Dashboard tổng quan", "DASHBOARD", "VIEW", "Xem các chỉ số và hoạt động tổng quan", 1),
            // STATISTICS
            new PermissionSpec("STATISTICS_VIEW", "Xem thống kê hệ thống", "STATISTICS", "VIEW", "Xem biểu đồ tăng trưởng và phân tích", 2),
            // PLACES
            new PermissionSpec("PLACES_VIEW", "Xem danh sách địa điểm", "PLACES", "VIEW", "Xem danh sách và chi tiết địa điểm", 10),
            new PermissionSpec("PLACES_CREATE", "Thêm địa điểm mới", "PLACES", "CREATE", "Thêm địa điểm du lịch vào hệ thống", 11),
            new PermissionSpec("PLACES_UPDATE", "Cập nhật địa điểm", "PLACES", "UPDATE", "Sửa đổi thông tin địa điểm", 12),
            new PermissionSpec("PLACES_DELETE", "Xóa địa điểm", "PLACES", "DELETE", "Xóa vĩnh viễn địa điểm khỏi hệ thống", 13),
            new PermissionSpec("PLACES_APPROVE", "Duyệt địa điểm đóng góp", "PLACES", "APPROVE", "Phê duyệt địa điểm người dùng đóng góp", 14),
            new PermissionSpec("PLACES_REJECT", "Từ chối địa điểm đóng góp", "PLACES", "REJECT", "Từ chối và loại bỏ địa điểm người dùng đóng góp", 15),
            // REVIEWS
            new PermissionSpec("REVIEWS_VIEW", "Xem đánh giá", "REVIEWS", "VIEW", "Xem danh sách và bộ lọc đánh giá", 20),
            new PermissionSpec("REVIEWS_HIDE", "Ẩn đánh giá vi phạm", "REVIEWS", "HIDE", "Tạm ẩn đánh giá có nội dung xấu", 21),
            new PermissionSpec("REVIEWS_RESTORE", "Khôi phục đánh giá", "REVIEWS", "RESTORE", "Khôi phục đánh giá đã bị ẩn", 22),
            new PermissionSpec("REVIEWS_DELETE", "Xóa đánh giá", "REVIEWS", "DELETE", "Xóa vĩnh viễn đánh giá vi phạm", 23),
            // REPORTS
            new PermissionSpec("REPORTS_VIEW", "Xem danh sách báo cáo", "REPORTS", "VIEW", "Xem danh sách bài viết & bình luận bị tố cáo", 30),
            new PermissionSpec("REPORTS_RESOLVE", "Xử lý tố cáo vi phạm", "REPORTS", "RESOLVE", "Duyệt xử lý vi phạm (Ẩn bài viết / phạt cảnh cáo)", 31),
            new PermissionSpec("REPORTS_DISMISS", "Bác bỏ tố cáo", "REPORTS", "DISMISS", "Bác bỏ tố cáo không hợp lệ hoặc sai sự thật", 32),
            // POSTS
            new PermissionSpec("POSTS_VIEW", "Xem bài viết cộng đồng", "POSTS", "VIEW", "Xem danh sách bài viết người dùng", 40),
            new PermissionSpec("POSTS_HIDE", "Ẩn bài viết vi phạm", "POSTS", "HIDE", "Ẩn bài viết khỏi newfeed cộng đồng", 41),
            new PermissionSpec("POSTS_RESTORE", "Khôi phục bài viết", "POSTS", "RESTORE", "Hiện lại bài viết đã bị ẩn", 42),
            new PermissionSpec("POSTS_DELETE", "Xóa bài viết", "POSTS", "DELETE", "Xóa bài viết", 43),
            // USERS
            new PermissionSpec("USERS_VIEW", "Xem danh sách người dùng", "USERS", "VIEW", "Xem hồ sơ và trạng thái tài khoản người dùng", 50),
            new PermissionSpec("USERS_LOCK", "Khóa tài khoản", "USERS", "LOCK", "Tạm khóa hoặc khóa vĩnh viễn tài khoản người dùng", 51),
            new PermissionSpec("USERS_UNLOCK", "Mở khóa tài khoản", "USERS", "UNLOCK", "Mở khóa tài khoản đã bị khóa", 52),
            new PermissionSpec("USERS_ASSIGN_ROLE", "Gán vai trò người dùng", "USERS", "ASSIGN_ROLE", "Phân quyền vai trò người dùng / moderator", 53),
            // MODERATORS
            new PermissionSpec("MODERATORS_VIEW", "Xem danh sách người giám sát", "MODERATORS", "VIEW", "Xem danh sách tài khoản moderator và quyền hạn", 60),
            new PermissionSpec("MODERATORS_MANAGE_PERMISSIONS", "Phân quyền người giám sát", "MODERATORS", "MANAGE_PERMISSIONS", "Cấp hoặc từ chối quyền chi tiết cho moderator", 61),
            // THEME & BRANDING
            new PermissionSpec("THEME_VIEW", "Xem giao diện & thương hiệu", "THEME", "VIEW", "Xem cấu hình màu sắc, logo và token giao diện", 70),
            new PermissionSpec("THEME_EDIT", "Chỉnh sửa giao diện nháp", "THEME", "EDIT", "Sửa đổi màu sắc, logo, ảnh nền và lưu nháp", 71),
            new PermissionSpec("THEME_PUBLISH", "Xuất bản giao diện", "THEME", "PUBLISH", "Áp dụng theme ra toàn bộ hệ thống", 72),
            new PermissionSpec("THEME_RESET", "Khôi phục giao diện mặc định", "THEME", "RESET", "Khôi phục theme về bảng màu chuẩn của Wayfare", 73),
            // SETTINGS
            new PermissionSpec("SETTINGS_VIEW", "Xem cấu hình hệ thống", "SETTINGS", "VIEW", "Xem thông tin cấu hình và tham số hệ thống", 80),
            new PermissionSpec("SETTINGS_EDIT", "Chỉnh sửa cấu hình hệ thống", "SETTINGS", "EDIT", "Cập nhật tham số hệ thống", 81),
            // AUDIT
            new PermissionSpec("AUDIT_VIEW", "Xem nhật ký kiểm duyệt", "AUDIT", "VIEW", "Xem nhật ký hoạt động và kiểm duyệt hệ thống", 90),
            new PermissionSpec("AUDIT_EXPORT", "Xuất file nhật ký", "AUDIT", "EXPORT", "Xuất file báo cáo lịch sử thao tác", 91)
        );

        Map<String, Permission> persistedPermissions = new HashMap<>();
        for (PermissionSpec spec : specs) {
            Permission p = permissionRepository.findByCode(spec.code())
                .orElseGet(() -> permissionRepository.save(
                    Permission.builder()
                        .code(spec.code())
                        .name(spec.name())
                        .module(spec.module())
                        .action(spec.action())
                        .description(spec.description())
                        .sortOrder(spec.sortOrder())
                        .isSystem(true)
                        .build()
                ));
            persistedPermissions.put(p.getCode(), p);
        }

        // 2. Ensure ROLE_ADMIN and ROLE_MODERATOR exist
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
            .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_ADMIN").description("Quản trị viên tối cao").build()));

        Role moderatorRole = roleRepository.findByName("ROLE_MODERATOR")
            .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_MODERATOR").description("Người giám sát nội dung").build()));

        // 3. Assign all permissions to ROLE_ADMIN
        for (Permission p : persistedPermissions.values()) {
            if (!rolePermissionRepository.existsByRoleIdAndPermissionId(adminRole.getId(), p.getId())) {
                rolePermissionRepository.save(RolePermission.builder().role(adminRole).permission(p).build());
            }
        }

        // 4. Assign default baseline permissions to ROLE_MODERATOR
        List<String> defaultModCodes = List.of(
            "DASHBOARD_VIEW",
            "PLACES_VIEW", "PLACES_APPROVE", "PLACES_REJECT",
            "REVIEWS_VIEW", "REVIEWS_HIDE", "REVIEWS_RESTORE",
            "REPORTS_VIEW", "REPORTS_RESOLVE", "REPORTS_DISMISS",
            "POSTS_VIEW", "POSTS_HIDE", "POSTS_RESTORE"
        );

        for (String code : defaultModCodes) {
            Permission p = persistedPermissions.get(code);
            if (p != null && !rolePermissionRepository.existsByRoleIdAndPermissionId(moderatorRole.getId(), p.getId())) {
                rolePermissionRepository.save(RolePermission.builder().role(moderatorRole).permission(p).build());
            }
        }

        log.info("Đã hoàn tất khởi tạo danh mục quyền RBAC ({}) mục.", persistedPermissions.size());
    }

    private record PermissionSpec(
        String code, String name, String module, String action, String description, int sortOrder
    ) {}

    /**
     * Get list of all registered system permissions grouped by module
     */
    @Transactional(readOnly = true)
    public List<PermissionDto> getAllPermissions() {
        return permissionRepository.findAllByOrderByModuleAscSortOrderAsc().stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Get effective permissions for a user given their ID
     */
    @Transactional(readOnly = true)
    public EffectivePermissionsDto getEffectivePermissions(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng có ID: " + userId));

        Set<String> roleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        boolean isSuperAdmin = roleNames.contains("ROLE_ADMIN");

        // Inherited from roles
        Set<Long> roleIds = user.getRoles().stream().map(Role::getId).collect(Collectors.toSet());
        Set<String> inherited = roleIds.isEmpty()
            ? Collections.emptySet()
            : rolePermissionRepository.findPermissionCodesByRoleIds(roleIds);

        // User overrides
        List<UserPermission> userPerms = userPermissionRepository.findByUserId(userId);
        Set<String> allowed = userPerms.stream()
            .filter(up -> "ALLOW".equalsIgnoreCase(up.getEffect()))
            .map(up -> up.getPermission().getCode())
            .collect(Collectors.toSet());

        Set<String> denied = userPerms.stream()
            .filter(up -> "DENY".equalsIgnoreCase(up.getEffect()))
            .map(up -> up.getPermission().getCode())
            .collect(Collectors.toSet());

        // Effective calculation: inherited + allow - deny (SuperAdmin gets all permissions)
        Set<String> effective = new HashSet<>();
        if (isSuperAdmin) {
            effective = permissionRepository.findAll().stream().map(Permission::getCode).collect(Collectors.toSet());
        } else {
            effective.addAll(inherited);
            effective.addAll(allowed);
            effective.removeAll(denied);
        }

        return EffectivePermissionsDto.builder()
            .userId(user.getId())
            .userEmail(user.getEmail())
            .userName(user.getFullName())
            .roles(roleNames)
            .inheritedPermissions(inherited)
            .allowedPermissions(allowed)
            .deniedPermissions(denied)
            .effectivePermissions(effective)
            .isSuperAdmin(isSuperAdmin)
            .build();
    }

    /**
     * Get effective permission codes for authentication checks (with fast cache)
     */
    @Transactional(readOnly = true)
    public Set<String> getEffectivePermissionCodesByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Collections.emptySet();
        }

        CacheEntry cached = permissionCache.get(email);
        if (cached != null && !cached.isExpired()) {
            return cached.permissions();
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return Collections.emptySet();
        }

        User user = userOpt.get();
        Set<String> roleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());

        Set<String> effective = new HashSet<>();
        if (roleNames.contains("ROLE_ADMIN")) {
            effective = permissionRepository.findAll().stream().map(Permission::getCode).collect(Collectors.toSet());
        } else {
            Set<Long> roleIds = user.getRoles().stream().map(Role::getId).collect(Collectors.toSet());
            Set<String> inherited = roleIds.isEmpty() ? Collections.emptySet() : rolePermissionRepository.findPermissionCodesByRoleIds(roleIds);
            List<UserPermission> userPerms = userPermissionRepository.findByUserId(user.getId());

            Set<String> allowed = userPerms.stream()
                .filter(up -> "ALLOW".equalsIgnoreCase(up.getEffect()))
                .map(up -> up.getPermission().getCode())
                .collect(Collectors.toSet());

            Set<String> denied = userPerms.stream()
                .filter(up -> "DENY".equalsIgnoreCase(up.getEffect()))
                .map(up -> up.getPermission().getCode())
                .collect(Collectors.toSet());

            effective.addAll(inherited);
            effective.addAll(allowed);
            effective.removeAll(denied);
        }

        permissionCache.put(email, new CacheEntry(effective, System.currentTimeMillis()));
        return effective;
    }

    /**
     * Check if user email holds a specific permission
     */
    public boolean hasPermission(String email, String permissionCode) {
        if (email == null || permissionCode == null) return false;
        Set<String> perms = getEffectivePermissionCodesByEmail(email);
        return perms.contains(permissionCode);
    }

    /**
     * List all moderators with their permissions summary
     */
    @Transactional(readOnly = true)
    public List<ModeratorDto> getAllModerators() {
        Role modRole = roleRepository.findByName("ROLE_MODERATOR").orElse(null);
        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElse(null);

        List<User> users = userRepository.findAll();
        List<ModeratorDto> results = new ArrayList<>();

        for (User u : users) {
            Set<String> roles = u.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
            if (roles.contains("ROLE_MODERATOR") || roles.contains("ROLE_ADMIN")) {
                EffectivePermissionsDto eff = getEffectivePermissions(u.getId());
                results.add(ModeratorDto.builder()
                    .id(u.getId())
                    .fullName(u.getFullName())
                    .email(u.getEmail())
                    .handle(u.getHandle())
                    .avatarUrl(u.getAvatarUrl())
                    .isLocked(u.getIsLocked())
                    .status(u.getStatus())
                    .lastLoginAt(u.getLastLoginAt())
                    .createdAt(u.getCreatedAt())
                    .roles(roles)
                    .effectivePermissionsCount(eff.getEffectivePermissions().size())
                    .customAllowedCount(eff.getAllowedPermissions().size())
                    .customDeniedCount(eff.getDeniedPermissions().size())
                    .build());
            }
        }
        return results;
    }

    /**
     * Get specific user permissions overrides
     */
    @Transactional(readOnly = true)
    public List<UserPermissionDto> getUserPermissions(Long userId) {
        return userPermissionRepository.findByUserId(userId).stream()
            .map(up -> UserPermissionDto.builder()
                .id(up.getId())
                .permissionId(up.getPermission().getId())
                .permissionCode(up.getPermission().getCode())
                .permissionName(up.getPermission().getName())
                .module(up.getPermission().getModule())
                .action(up.getPermission().getAction())
                .effect(up.getEffect())
                .grantedBy(up.getGrantedBy())
                .grantedAt(up.getGrantedAt())
                .build())
            .collect(Collectors.toList());
    }

    /**
     * Update permissions for a specific user (ALLOW / DENY overrides)
     */
    @Transactional
    public EffectivePermissionsDto updateUserPermissions(
        Long adminId, Long targetUserId, List<String> allowCodes, List<String> denyCodes,
        String ipAddress, String userAgent
    ) {
        User targetUser = userRepository.findById(targetUserId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng có ID: " + targetUserId));

        // Security check: cannot modify permissions of SuperAdmin via this endpoint
        boolean isTargetAdmin = targetUser.getRoles().stream().anyMatch(r -> "ROLE_ADMIN".equals(r.getName()));
        if (isTargetAdmin) {
            throw new IllegalStateException("Không thể sửa đổi phân quyền của tài khoản Quản trị viên tối cao (Super Admin).");
        }

        // Get before state for audit log
        EffectivePermissionsDto beforeState = getEffectivePermissions(targetUserId);

        // Clear existing overrides
        userPermissionRepository.deleteByUserId(targetUserId);

        List<Permission> allPerms = permissionRepository.findAll();
        Map<String, Permission> permMap = allPerms.stream().collect(Collectors.toMap(Permission::getCode, p -> p));

        // Save ALLOW overrides
        if (allowCodes != null) {
            for (String code : allowCodes) {
                Permission p = permMap.get(code);
                if (p != null) {
                    userPermissionRepository.save(UserPermission.builder()
                        .user(targetUser)
                        .permission(p)
                        .effect("ALLOW")
                        .grantedBy(adminId)
                        .build());
                }
            }
        }

        // Save DENY overrides
        if (denyCodes != null) {
            for (String code : denyCodes) {
                Permission p = permMap.get(code);
                if (p != null && (allowCodes == null || !allowCodes.contains(code))) {
                    userPermissionRepository.save(UserPermission.builder()
                        .user(targetUser)
                        .permission(p)
                        .effect("DENY")
                        .grantedBy(adminId)
                        .build());
                }
            }
        }

        // Invalidate cache immediately
        clearUserCache(targetUser.getEmail());

        // Get after state
        EffectivePermissionsDto afterState = getEffectivePermissions(targetUserId);

        // Audit log
        User adminUser = adminId != null ? userRepository.findById(adminId).orElse(null) : null;
        activityLogService.recordLog(
            adminUser,
            "UPDATE_PERMISSIONS",
            String.format("Cập nhật quyền cho người giám sát %s (%s). Cho phép: %s, Từ chối: %s",
                targetUser.getFullName(), targetUser.getEmail(),
                allowCodes != null ? String.join(", ", allowCodes) : "[]",
                denyCodes != null ? String.join(", ", denyCodes) : "[]"
            ),
            ipAddress,
            userAgent
        );

        return afterState;
    }

    private PermissionDto toDto(Permission p) {
        return PermissionDto.builder()
            .id(p.getId())
            .code(p.getCode())
            .name(p.getName())
            .module(p.getModule())
            .action(p.getAction())
            .description(p.getDescription())
            .isSystem(p.getIsSystem())
            .sortOrder(p.getSortOrder())
            .build();
    }
}
