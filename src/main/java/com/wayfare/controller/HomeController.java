package com.wayfare.controller;

import com.wayfare.dto.ApiResponse;
import com.wayfare.dto.HomeStatsDto;
import com.wayfare.dto.PostDto;
import com.wayfare.service.HomeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
@Slf4j
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<HomeStatsDto>> getHomeStats() {
        log.info("REST request to get public home statistics");
        HomeStatsDto stats = homeService.getHomeStats();
        return ResponseEntity.ok(ApiResponse.success("Lấy thống kê trang chủ thành công", stats));
    }

    @GetMapping("/featured-itineraries")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFeaturedItineraries(
            @RequestParam(value = "region", required = false, defaultValue = "all") String region) {
        log.info("REST request to get featured itineraries for region: {}", region);
        List<Map<String, Object>> tours = homeService.getFeaturedItineraries(region);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách lộ trình nổi bật thành công", tours));
    }

    @GetMapping("/community-highlights")
    public ResponseEntity<ApiResponse<List<PostDto>>> getCommunityHighlights() {
        log.info("REST request to get community highlights for home page");
        List<PostDto> posts = homeService.getCommunityHighlights();
        return ResponseEntity.ok(ApiResponse.success("Lấy bài viết cộng đồng tiêu biểu thành công", posts));
    }

    @GetMapping("/trending-destinations")
    public ResponseEntity<ApiResponse<List<String>>> getTrendingDestinations() {
        log.info("REST request to get trending destinations");
        List<String> list = homeService.getTrendingDestinations();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách điểm đến thịnh hành thành công", list));
    }
}
