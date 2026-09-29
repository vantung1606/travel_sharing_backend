package com.wayfare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostDto {
    private Long id;
    private String title;
    private String content;
    private String locationTag;
    private String imageUrl;
    private List<String> images;
    private Integer likeCount;
    private Integer commentCount;
    private Boolean isLiked;
    private String category;
    private String status;
    private Integer aiSafetyScore;
    private String aiFlagReason;
    private String badgeText;
    private LocalDateTime createdAt;
    private String videoUrl;
    private String visibility; // PUBLIC, PRIVATE
    private String timeAgo;
    private String formattedDate;
    private Boolean isOwner;

    // Author Info
    private Long authorId;
    private String authorName;
    private String authorEmail;
    private String authorHandle;
    private String authorAvatar;
    private String authorRole;

    // Attached Itinerary (Link to My Itineraries)
    private Long itineraryId;
    private String itineraryTitle;
    private String itineraryDestination;
    private String itineraryDuration;
    private Long itineraryBudget;
    private Integer itineraryPlacesCount;
    private Boolean itineraryIsAi;

    public java.util.Map<String, Object> getAuthor() {
        java.util.Map<String, Object> authorMap = new java.util.HashMap<>();
        authorMap.put("id", authorId != null ? authorId : 1L);
        authorMap.put("fullName", authorName != null ? authorName : "Thành viên Wayfare");
        authorMap.put("name", authorName != null ? authorName : "Thành viên Wayfare");
        authorMap.put("email", authorEmail);
        authorMap.put("handle", authorHandle != null ? authorHandle : "@wayfarer");
        authorMap.put("avatar", authorAvatar != null ? authorAvatar : "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80");
        authorMap.put("role", authorRole != null ? authorRole : "Phượt thủ tự do");
        return authorMap;
    }
}
