package com.wayfare.service;

import com.wayfare.dto.AdminStatsDto;
import com.wayfare.repository.ItineraryRepository;
import com.wayfare.repository.PlaceRepository;
import com.wayfare.repository.PostRepository;
import com.wayfare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PlaceRepository placeRepository;
    private final ItineraryRepository itineraryRepository;

    public AdminStatsDto getSystemStats() {
        log.info("Fetching admin system statistics");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime sixMonthsAgo = now.minusMonths(6).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        // ---- Counts ----
        long totalUsers = userRepository.count();
        long newUsersThisMonth = userRepository.countByCreatedAtAfter(startOfMonth);

        long totalPosts = postRepository.count();
        long newPostsThisMonth = postRepository.countByCreatedAtAfter(startOfMonth);

        long totalPlaces = placeRepository.count();

        long totalItineraries = itineraryRepository.count();
        long newItinerariesThisMonth = itineraryRepository.countByCreatedAtAfter(startOfMonth);
        long activeItineraries = itineraryRepository.countByStatus("ACTIVE");

        // ---- Engagement ----
        Long totalLikes = postRepository.sumAllLikes();
        Long totalComments = postRepository.sumAllComments();
        Double averageRating = placeRepository.findOverallAverageRating();

        // ---- Monthly growth (last 6 months) ----
        List<AdminStatsDto.MonthlyStats> userGrowth = buildMonthlyStats(
                userRepository.countUsersByMonth(sixMonthsAgo), sixMonthsAgo);
        List<AdminStatsDto.MonthlyStats> postGrowth = buildMonthlyStats(
                postRepository.countPostsByMonth(sixMonthsAgo), sixMonthsAgo);

        // ---- Top destinations ----
        List<Object[]> destRaw = itineraryRepository.findTopDestinations(PageRequest.of(0, 5));
        List<AdminStatsDto.TopDestination> topDestinations = destRaw.stream()
                .map(row -> AdminStatsDto.TopDestination.builder()
                        .destination((String) row[0])
                        .count(((Number) row[1]).longValue())
                        .build())
                .toList();

        // ---- Top places ----
        List<AdminStatsDto.TopPlace> topPlaces = placeRepository
                .findTopPlacesByRating(PageRequest.of(0, 5))
                .stream()
                .map(p -> AdminStatsDto.TopPlace.builder()
                        .name(p.getName())
                        .city(p.getCity())
                        .rating(p.getAverageRating() != null ? p.getAverageRating().doubleValue() : 0)
                        .reviewCount(p.getReviewCount())
                        .build())
                .toList();

        log.info("Admin stats: {} users, {} posts, {} places, {} itineraries",
                totalUsers, totalPosts, totalPlaces, totalItineraries);

        return AdminStatsDto.builder()
                .totalUsers(totalUsers)
                .newUsersThisMonth(newUsersThisMonth)
                .totalPosts(totalPosts)
                .newPostsThisMonth(newPostsThisMonth)
                .totalPlaces(totalPlaces)
                .totalItineraries(totalItineraries)
                .newItinerariesThisMonth(newItinerariesThisMonth)
                .activeItineraries(activeItineraries)
                .totalLikes(totalLikes != null ? totalLikes : 0)
                .totalComments(totalComments != null ? totalComments : 0)
                .averagePlaceRating(averageRating != null ? averageRating : 0.0)
                .userGrowth(userGrowth)
                .postGrowth(postGrowth)
                .topDestinations(topDestinations)
                .topPlaces(topPlaces)
                .build();
    }

    /**
     * Builds a 6-slot monthly stats list, filling 0 for months with no data.
     */
    private List<AdminStatsDto.MonthlyStats> buildMonthlyStats(List<Object[]> rawData, LocalDateTime from) {
        List<AdminStatsDto.MonthlyStats> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/yyyy");

        // Build map: "MM/yyyy" -> count
        java.util.Map<String, Long> dataMap = new java.util.HashMap<>();
        for (Object[] row : rawData) {
            int month = ((Number) row[0]).intValue();
            int year = ((Number) row[1]).intValue();
            long count = ((Number) row[2]).longValue();
            String key = String.format("%02d/%04d", month, year);
            dataMap.put(key, count);
        }

        // Generate last 6 month slots
        for (int i = 5; i >= 0; i--) {
            LocalDateTime slot = from.plusMonths(5 - i);
            String key = slot.format(fmt);
            String label = "Th" + slot.getMonthValue() + "/" + slot.getYear();
            result.add(AdminStatsDto.MonthlyStats.builder()
                    .label(label)
                    .count(dataMap.getOrDefault(key, 0L))
                    .build());
        }

        return result;
    }
}
