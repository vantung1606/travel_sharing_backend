package com.wayfare.modules.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMemberDto {
    private Long userId;
    private String fullName;
    private String handle;
    private String email;
    private String avatarUrl;
    private String role;
    private String joinedAt;
    private Boolean isOnline;
    private String statusText;
    private LocalDateTime lastActiveAt;
}
