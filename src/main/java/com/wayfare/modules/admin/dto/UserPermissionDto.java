package com.wayfare.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissionDto {
    private Long id;
    private Long permissionId;
    private String permissionCode;
    private String permissionName;
    private String module;
    private String action;
    private String effect; // ALLOW, DENY
    private Long grantedBy;
    private LocalDateTime grantedAt;
}
