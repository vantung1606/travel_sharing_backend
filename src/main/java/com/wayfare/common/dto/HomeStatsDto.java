package com.wayfare.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomeStatsDto {
    private long totalItineraries;
    private long totalPlaces;
    private long totalPosts;
    private long totalUsers;
    private long totalLikes;
    private long totalComments;
}

