package com.wayfare.common.service;

import com.wayfare.modules.community.dto.PostDto;
import com.wayfare.modules.community.service.PostService;
import com.wayfare.common.dto.HomeStatsDto;

import com.wayfare.entity.Itinerary;
import com.wayfare.entity.Post;
import com.wayfare.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomeService {

    private final ItineraryRepository itineraryRepository;
    private final PlaceRepository placeRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostService postService;

    @Transactional(readOnly = true)
    public HomeStatsDto getHomeStats() {
        log.info("Computing public home statistics");
        long itinCount = itineraryRepository.count();
        long placeCount = placeRepository.count();
        long postCount = postRepository.count();
        long userCount = userRepository.count();
        Long totalLikes = postRepository.sumAllLikes();
        Long totalComments = postRepository.sumAllComments();

        return HomeStatsDto.builder()
                .totalItineraries(Math.max(itinCount, 128))
                .totalPlaces(Math.max(placeCount, 84))
                .totalPosts(Math.max(postCount, 320))
                .totalUsers(Math.max(userCount, 1450))
                .totalLikes(totalLikes != null ? Math.max(totalLikes, 5240) : 5240)
                .totalComments(totalComments != null ? Math.max(totalComments, 1890) : 1890)
                .build();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFeaturedItineraries(String region) {
        log.info("Fetching featured itineraries for region: {}", region);
        List<Itinerary> list = itineraryRepository.findAll();

        List<Map<String, Object>> result = new ArrayList<>();
        DecimalFormat df = new DecimalFormat("#,###");

        for (Itinerary itin : list) {
            String tourRegion = determineRegion(itin.getDestination());
            if (region != null && !"all".equalsIgnoreCase(region) && !tourRegion.equalsIgnoreCase(region)) {
                continue;
            }

            Map<String, Object> map = new HashMap<>();
            map.put("id", itin.getId());
            map.put("title", itin.getTitle());
            map.put("destination", itin.getDestination());
            map.put("location", itin.getDestination());
            map.put("region", tourRegion);
            map.put("desc", "Hành trình tối ưu thời gian và chi phí với sự đồng hành của trợ lý AI Wayfare.");
            map.put("image", itin.getCoverImageUrl() != null ? itin.getCoverImageUrl() : "https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=1200&q=85");
            map.put("duration", "3 Ngày 2 Đêm");
            map.put("tag", itin.getIsAiGenerated() ? "AI Thông Minh ✨" : "Thịnh hành 🔥");
            map.put("category", "Nghỉ dưỡng & Khám phá");
            map.put("rating", 4.9);
            map.put("saves", "1.250");
            map.put("price", itin.getBudgetTotal() != null ? df.format(itin.getBudgetTotal()) + "đ" : "3.500.000đ");
            result.add(map);
        }

        // Fallback default curated tours if database has fewer than 4 tours
        if (result.size() < 4) {
            List<Map<String, Object>> curated = getCuratedDefaultTours();
            for (Map<String, Object> c : curated) {
                String cReg = (String) c.get("region");
                if (region == null || "all".equalsIgnoreCase(region) || cReg.equalsIgnoreCase(region)) {
                    if (result.stream().noneMatch(r -> r.get("title").equals(c.get("title")))) {
                        result.add(c);
                    }
                }
            }
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<PostDto> getCommunityHighlights() {
        log.info("Fetching community highlights for homepage");
        try {
            // Get top active posts
            List<Post> posts = postRepository.findByStatusOrderByCreatedAtDesc("ACTIVE");
            if (posts.isEmpty()) {
                posts = postRepository.findAll();
            }
            return posts.stream()
                    .sorted((a, b) -> Integer.compare(
                            b.getLikeCount() != null ? b.getLikeCount() : 0,
                            a.getLikeCount() != null ? a.getLikeCount() : 0
                    ))
                    .limit(4)
                    .map(p -> postService.getPostById(p.getId(), "admin@gmail.com"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Error fetching community highlights: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<String> getTrendingDestinations() {
        return List.of(
                "Đà Lạt",
                "Phú Quốc",
                "Sa Pa",
                "Đà Nẵng & Hội An",
                "Mù Cang Chải",
                "Ninh Bình",
                "Hà Giang"
        );
    }

    private String determineRegion(String dest) {
        if (dest == null) return "bac";
        String lower = dest.toLowerCase();
        if (lower.contains("đà nẵng") || lower.contains("hội an") || lower.contains("huế") || lower.contains("quy nhơn") || lower.contains("nha trang")) {
            return "trung";
        }
        if (lower.contains("phú quốc") || lower.contains("sài gòn") || lower.contains("hồ chí minh") || lower.contains("vũng tàu") || lower.contains("cần thơ")) {
            return "nam";
        }
        return "bac";
    }

    private List<Map<String, Object>> getCuratedDefaultTours() {
        List<Map<String, Object>> list = new ArrayList<>();

        Map<String, Object> t1 = new HashMap<>();
        t1.put("id", 1L);
        t1.put("region", "trung");
        t1.put("title", "Đà Nẵng & Hội An: Biển Xanh & Phố Cổ Lung Linh");
        t1.put("desc", "Nghỉ dưỡng bãi biển Mỹ Khê, chèo SUP bán đảo Sơn Trà và thưởng thức ẩm thực đêm phố đèn lồng Hội An.");
        t1.put("duration", "4 Ngày 3 Đêm");
        t1.put("tag", "Tiết kiệm 20%");
        t1.put("category", "Chill & Ẩm thực");
        t1.put("rating", 4.9);
        t1.put("saves", "1.420");
        t1.put("price", "3.850.000đ");
        t1.put("location", "Miền Trung");
        t1.put("image", "https://images.unsplash.com/photo-1559592413-7cec4d0cae2b?auto=format&fit=crop&w=1200&q=85");
        list.add(t1);

        Map<String, Object> t2 = new HashMap<>();
        t2.put("id", 2L);
        t2.put("region", "bac");
        t2.put("title", "Mù Cang Chải & Sa Pa: Biển Vàng Mây Ngàn Tây Bắc");
        t2.put("desc", "Chinh phục đèo Khau Phạ hùng vĩ, chiêm ngưỡng đồi Mâm Xôi mùa lúa chín và săn mây đỉnh Fansipan.");
        t2.put("duration", "3 Ngày 2 Đêm");
        t2.put("tag", "Trending 🔥");
        t2.put("category", "Nhiếp ảnh & Săn mây");
        t2.put("rating", 4.95);
        t2.put("saves", "2.840");
        t2.put("price", "2.950.000đ");
        t2.put("location", "Tây Bắc");
        t2.put("image", "https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=1200&q=85");
        list.add(t2);

        Map<String, Object> t3 = new HashMap<>();
        t3.put("id", 3L);
        t3.put("region", "nam");
        t3.put("title", "Phú Quốc: Thiên Đường Hoàng Hôn Đảo Ngọc");
        t3.put("desc", "Trải nghiệm cáp treo vượt biển dài nhất thế giới, lặn ngắm san hô Hòn Mây Rút và tiệc cocktail bãi biển.");
        t3.put("duration", "3 Ngày 2 Đêm");
        t3.put("tag", "Độc quyền AI");
        t3.put("category", "Nghỉ dưỡng & Hải sản");
        t3.put("rating", 4.88);
        t3.put("saves", "950");
        t3.put("price", "5.600.000đ");
        t3.put("location", "Kiên Giang");
        t3.put("image", "https://images.unsplash.com/photo-1589394815804-964ed0be2eb5?auto=format&fit=crop&w=1200&q=85");
        list.add(t3);

        Map<String, Object> t4 = new HashMap<>();
        t4.put("id", 4L);
        t4.put("region", "bac");
        t4.put("title", "Ninh Bình: Tuyệt Tác Di Sản Tràng An & Hang Múa");
        t4.put("desc", "Xuôi thuyền khám phá thủy động kỳ vĩ, chinh phục đỉnh ngọa long Hang Múa ngắm trọn toàn cảnh Tam Cốc.");
        t4.put("duration", "2 Ngày 1 Đêm");
        t4.put("tag", "Cuối tuần 🌿");
        t4.put("category", "Di sản & Thiên nhiên");
        t4.put("rating", 4.92);
        t4.put("saves", "3.120");
        t4.put("price", "1.850.000đ");
        t4.put("location", "Ninh Bình");
        t4.put("image", "https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=1200&q=85");
        list.add(t4);

        return list;
    }
}


