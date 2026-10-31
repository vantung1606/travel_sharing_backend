package com.wayfare.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModeratorDto {
    private Long id;
    private String fullName;
    private String email;
    private String handle;
    private String avatarUrl;
    private Boolean isLocked;
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;

    @Builder.Default
    private Set<String> roles = new HashSet<>();

    private int effectivePermissionsCount;
    private int customAllowedCount;
    private int customDeniedCount;
}
