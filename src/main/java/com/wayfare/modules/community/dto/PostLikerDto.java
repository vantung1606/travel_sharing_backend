package com.wayfare.modules.community.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostLikerDto {
    private Long id;
    private String fullName;
    private String handle;
    private String avatarUrl;
    private String bio;
    private String role;
    private Boolean isFollowing;
    private Boolean isMe;
    private LocalDateTime likedAt;
}
