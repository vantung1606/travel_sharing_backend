package com.wayfare.modules.user.dto;

import com.wayfare.modules.itinerary.dto.ItineraryDto;
import com.wayfare.modules.community.dto.PostDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDto {
    private Long id;
    private String fullName;
    private String handle;
    private String email;
    private String avatarUrl;
    private String bio;
    private String travelStyle;
    private String budgetPreference;
    private Boolean isVerified;
    private String coverImageUrl;
    private String location;
    private String rank;
    private String role; // "Quản trị viên" | "Phượt thủ tự do" | "Wanderer Gold"

    private Long followersCount;
    private Long followingCount;
    private Integer postsCount;
    private Integer itinerariesCount;
    private Boolean isFollowing;

    private List<PostDto> posts;
    private List<ItineraryDto> itineraries;
}


