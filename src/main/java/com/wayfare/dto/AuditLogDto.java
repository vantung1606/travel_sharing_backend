package com.wayfare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDto {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String userHandle;
    private String userAvatar;
    private String userRole;
    private String action;
    private String details;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
}
