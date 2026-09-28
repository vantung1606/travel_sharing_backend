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
    private String badgeText;
    private LocalDateTime createdAt;
    private String timeAgo;

    // Author Info
    private Long authorId;
    private String authorName;
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
}
