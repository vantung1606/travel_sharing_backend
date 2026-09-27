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
public class AdminPostDto {
    private Long id;
    private String postCode; // PST-89021
    private String title;
    private String content;
    private String snippet;
    private String category;
    private String categoryType; // error, warning, info
    private String locationTag;
    private String imageUrl;
    private Integer likeCount;
    private Integer commentCount;
    private Integer reportsCount;
    private String status; // ACTIVE, PENDING_REPORT, HIDDEN, REMOVED
    private Integer aiSafetyScore;
    private String aiFlagReason;
    private String reportReason;
    private String badgeText;
    private LocalDateTime createdAt;
    private String timeAgo;
    
    // Author info
    private Long authorId;
    private String authorName;
    private String authorHandle;
    private String authorAvatar;
    private Integer authorTrustScore;
    private String authorAccountAge;
}
