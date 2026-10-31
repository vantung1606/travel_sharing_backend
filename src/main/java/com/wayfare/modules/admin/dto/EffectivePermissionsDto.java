package com.wayfare.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffectivePermissionsDto {
    private Long userId;
    private String userEmail;
    private String userName;

    @Builder.Default
    private Set<String> roles = new HashSet<>();

    @Builder.Default
    private Set<String> inheritedPermissions = new HashSet<>();

    @Builder.Default
    private Set<String> allowedPermissions = new HashSet<>();

    @Builder.Default
    private Set<String> deniedPermissions = new HashSet<>();

    @Builder.Default
    private Set<String> effectivePermissions = new HashSet<>();

    private boolean isSuperAdmin;
}
