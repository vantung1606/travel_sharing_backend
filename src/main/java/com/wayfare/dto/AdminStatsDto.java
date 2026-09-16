package com.wayfare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsDto {

    // ---- Overview counts ----
    private long totalUsers;
    private long newUsersThisMonth;
    private long totalPosts;
    private long newPostsThisMonth;
    private long totalPlaces;
    private long totalItineraries;
    private long newItinerariesThisMonth;
    private long activeItineraries;

    // ---- Engagement ----
    private long totalLikes;
    private long totalComments;
    private double averagePlaceRating;

    // ---- Monthly chart data (last 6 months) ----
    private List<MonthlyStats> userGrowth;
    private List<MonthlyStats> postGrowth;

    // ---- Top data ----
    private List<TopDestination> topDestinations;
    private List<TopPlace> topPlaces;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyStats {
        private String label; // e.g. "Th9/2026"
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopDestination {
        private String destination;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopPlace {
        private String name;
        private String city;
        private double rating;
        private int reviewCount;
    }
}
