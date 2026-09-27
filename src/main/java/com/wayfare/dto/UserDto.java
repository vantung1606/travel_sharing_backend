package com.wayfare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private Long id;
    private String email;
    private String fullName;
    private String handle;
    private String avatarUrl;
    private String phoneNumber;
    private String bio;
    private String travelStyle;
    private String budgetPreference;
    private Boolean isVerified;
    private String status; // ACTIVE, LOCKED
    private Boolean isLocked;
    private Set<String> roles;
    private Integer tripsCount;
    private Integer postsCount;
    private Integer reviewsCount;
    private Integer trustScore;
    private Integer riskScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
