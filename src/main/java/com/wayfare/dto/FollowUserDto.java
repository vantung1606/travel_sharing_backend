package com.wayfare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowUserDto {
    private Long id;
    private String fullName;
    private String handle;
    private String avatarUrl;
    private String role;
    private String bio;
    private Boolean isFollowing;
    private Long followersCount;
}
