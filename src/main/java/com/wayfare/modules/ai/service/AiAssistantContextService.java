package com.wayfare.modules.ai.service;

import com.wayfare.entity.*;
import com.wayfare.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dịch vụ tổng hợp ngữ cảnh thực tế từ CSDL MySQL cho Trợ lý Wayfare AI.
 * Giúp AI đọc được địa điểm, tỉnh thành, lịch trình của người dùng, điểm dừng,
 * địa điểm đã lưu và bài viết cộng đồng để trả lời đúng trọng tâm và tạo nút thao tác thật.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiAssistantContextService {

    private final PlaceRepository placeRepository;
    private final ProvinceRepository provinceRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryDetailRepository itineraryDetailRepository;
    private final ItineraryExpenseRepository itineraryExpenseRepository;
    private final ReviewRepository reviewRepository;
    private final UserProvinceVisitRepository userProvinceVisitRepository;
    private final SavedPlaceRepository savedPlaceRepository;
    private final PostRepository postRepository;

    @Transactional(readOnly = true)
    public String buildDatabaseContext(User user, String currentQuestion, List<ChatMessage> recentMessages) {
        StringBuilder sb = new StringBuilder();
        try {
            // 1. Thông tin người dùng hiện tại + Dấu chân tỉnh thành đã đi
            sb.append("=== THÔNG TIN NGƯỜI DÙNG HIỆN TẠI ===\n");
            if (user != null) {
                sb.append(String.format("- ID: %d | Tên: %s | Cấp bậc: %s | Phong cách: %s | Ngân sách ưa thích: %s\n",
                        user.getId(),
                        safe(user.getFullName(), user.getEmail()),
                        safe(user.getTier(), "bronze"),
                        safe(user.getTravelStyle(), "Linh hoạt"),
                        safe(user.getBudgetPreference(), "Trung bình")));
                List<UserProvinceVisit> visits = userProvinceVisitRepository.findByUserIdOrderByVisitedAtDesc(user.getId());
                if (!visits.isEmpty()) {
                    sb.append("- Các mã tỉnh/thành đã từng check-in: ")
                            .append(visits.stream().limit(15).map(UserProvinceVisit::getProvinceCode).collect(Collectors.joining(", ")))
                            .append("\n");
                }
            }

            // 2. Ghép văn bản hội thoại gần đây để nhận diện địa danh / từ khóa ngữ cảnh
            String combinedContext = buildCombinedText(currentQuestion, recentMessages);
            String normCombined = normalize(combinedContext);

            // 3. Tỉnh/Thành phố trong hệ thống
            List<Province> provinces = provinceRepository.findAllByOrderByRegionAscNameAsc();
            List<String> matchedProvinces = provinces.stream()
                    .map(Province::getName)
                    .filter(Objects::nonNull)
                    .filter(name -> normCombined.contains(normalize(name)))
                    .collect(Collectors.toList());

            sb.append("\n=== TỈNH/THÀNH PHỐ TRONG HỆ THỐNG ===\n");
            if (!matchedProvinces.isEmpty()) {
                sb.append("Đang nhắc tới: ").append(String.join(", ", matchedProvinces)).append("\n");
            }
            sb.append("Danh sách tỉnh/thành (một phần): ")
                    .append(provinces.stream().limit(25).map(Province::getName).collect(Collectors.joining(", ")))
                    .append("\n");

            // 4. Địa điểm thật trong CSDL (Place)
            List<Place> matchedPlaces = findRelevantPlaces(combinedContext, normCombined, matchedProvinces);
            sb.append("\n=== ĐỊA ĐIỂM DU LỊCH THẬT TRONG CSDL (Dùng đúng placeId và tên khi gợi ý/tạo nút) ===\n");
            if (matchedPlaces.isEmpty()) {
                sb.append("(Chưa có địa điểm khớp trực tiếp trong CSDL; có thể gợi ý địa điểm nổi bật hoặc đề xuất thêm địa điểm)\n");
            } else {
                int reviewSampleCount = 0;
                for (Place p : matchedPlaces) {
                    sb.append(String.format("- [placeId=%d] %s | TP/Tỉnh: %s | Loại: %s | Đánh giá: %.1f (%d lượt) | Địa chỉ: %s\n",
                            p.getId(),
                            safe(p.getName(), ""),
                            safe(p.getCity(), ""),
                            safe(p.getCategoryName(), "Khác"),
                            p.getAverageRating() != null ? p.getAverageRating() : 0.0,
                            p.getReviewCount() != null ? p.getReviewCount() : 0,
                            safe(p.getAddress(), "")));
                    if (reviewSampleCount < 5) {
                        List<Review> revs = reviewRepository.findByPlaceIdOrderByCreatedAtDesc(p.getId());
                        if (!revs.isEmpty()) {
                            reviewSampleCount++;
                            String revSnippets = revs.stream().limit(2)
                                    .map(r -> String.format("%d★ \"%s\"",
                                            r.getRating() != null ? r.getRating() : 5,
                                            safe(r.getComment(), "").replaceAll("\\s+", " ").substring(0, Math.min(safe(r.getComment(), "").length(), 90))))
                                    .collect(Collectors.joining("; "));
                            sb.append("  Đánh giá thực tế: ").append(revSnippets).append("\n");
                        }
                    }
                }
            }

            // 5. Lịch trình cá nhân của người dùng (Itinerary + ItineraryDetail)
            if (user != null) {
                List<Itinerary> myItineraries = itineraryRepository.findByCreatorOrderByCreatedAtDesc(user);
                sb.append("\n=== LỊCH TRÌNH CỦA NGƯỜI DÙNG (Dùng đúng itineraryId & detailId khi Sửa/Xóa/Thêm điểm dừng) ===\n");
                if (myItineraries.isEmpty()) {
                    sb.append("(Người dùng chưa có lịch trình nào)\n");
                } else {
                    for (Itinerary it : myItineraries.stream().limit(6).collect(Collectors.toList())) {
                        String daysStr = "?";
                        if (it.getStartDate() != null && it.getEndDate() != null) {
                            long days = java.time.temporal.ChronoUnit.DAYS.between(it.getStartDate(), it.getEndDate()) + 1;
                            daysStr = String.valueOf(Math.max(1, days));
                        }
                        java.math.BigDecimal spent = itineraryExpenseRepository.getTotalExpensesByItineraryId(it.getId());
                        sb.append(String.format("- [itineraryId=%d] \"%s\" | Điểm đến: %s | Số ngày: %s | Ngân sách: %s VNĐ | Đã chi: %s VNĐ | Nhóm: %s | Trạng thái: %s\n",
                                it.getId(),
                                safe(it.getTitle(), "Chưa đặt tên"),
                                safe(it.getDestination(), ""),
                                daysStr,
                                it.getBudgetTotal() != null ? it.getBudgetTotal().toPlainString() : "0",
                                spent != null ? spent.toPlainString() : "0",
                                safe(it.getGroupType(), "Cá nhân"),
                                safe(it.getStatus(), "DRAFT")));

                        List<ItineraryDetail> details = itineraryDetailRepository.findByItineraryIdOrderByDayNumberAscVisitOrderAsc(it.getId());
                        if (!details.isEmpty()) {
                            String stopsSummary = details.stream().limit(10)
                                    .map(d -> String.format("[detailId=%d|Ngày %d|%s] %s",
                                            d.getId(),
                                            d.getDayNumber() != null ? d.getDayNumber() : 1,
                                            d.getStartTime() != null ? d.getStartTime().toString() : "--:--",
                                            safe(d.getLocationName(), "Điểm dừng")))
                                    .collect(Collectors.joining("; "));
                            sb.append("  Các chặng: ").append(stopsSummary).append("\n");
                        }
                    }
                }

                // 6. Địa điểm đã lưu của người dùng
                List<SavedPlace> saved = savedPlaceRepository.findAllByUserIdWithPlace(user.getId());
                sb.append("\n=== ĐỊA ĐIỂM ĐÃ LƯU (WISHLIST) CỦA NGƯỜI DÙNG ===\n");
                if (saved.isEmpty()) {
                    sb.append("(Chưa lưu địa điểm nào)\n");
                } else {
                    sb.append(saved.stream().limit(12)
                            .map(sp -> String.format("[placeId=%d] %s (%s)",
                                    sp.getPlace().getId(),
                                    sp.getPlace().getName(),
                                    safe(sp.getPlace().getCity(), "")))
                            .collect(Collectors.joining(", ")))
                            .append("\n");
                }
            }

            // 7. Bài viết cộng đồng liên quan
            String postKeyword = !matchedProvinces.isEmpty() ? matchedProvinces.get(0) : extractKeyword(currentQuestion);
            List<Post> posts = postRepository.searchPosts(
                    (postKeyword != null && !postKeyword.isBlank()) ? postKeyword : null,
                    "ACTIVE"
            );
            if (posts.isEmpty() && postKeyword != null) {
                posts = postRepository.findByStatusOrderByCreatedAtDesc("ACTIVE");
            }
            sb.append("\n=== BÀI VIẾT CỘNG ĐỒNG LIÊN QUAN ===\n");
            if (posts.isEmpty()) {
                sb.append("(Chưa có bài viết phù hợp)\n");
            } else {
                posts.stream()
                        .filter(p -> !"PRIVATE".equalsIgnoreCase(p.getVisibility()))
                        .limit(5)
                        .forEach(p -> sb.append(String.format("- [postId=%d] \"%s\" | Địa điểm: %s | Like: %d\n",
                                p.getId(),
                                safe(p.getTitle(), "Bài chia sẻ"),
                                safe(p.getLocationTag(), ""),
                                p.getLikeCount() != null ? p.getLikeCount() : 0)));
            }

            log.info("Built AI DB context ({} chars) for user {}", sb.length(), user != null ? user.getEmail() : "guest");
        } catch (Exception e) {
            log.error("Error building AI assistant DB context: {}", e.getMessage(), e);
        }
        return sb.toString();
    }

    private List<Place> findRelevantPlaces(String combinedContext, String normCombined, List<String> matchedProvinces) {
        Map<Long, Place> unique = new LinkedHashMap<>();

        // Ưu tiên tìm theo tỉnh/thành xuất hiện trong hội thoại
        for (String prov : matchedProvinces) {
            List<Place> byCity = placeRepository.searchPlaces("ACTIVE", prov, null, null);
            for (Place p : byCity) {
                unique.putIfAbsent(p.getId(), p);
                if (unique.size() >= 12) break;
            }
        }

        // Tìm thêm theo từ khóa cụ thể hoặc quét ACTIVE places
        if (unique.size() < 12) {
            String kw = extractKeyword(combinedContext);
            if (kw != null && kw.length() >= 2) {
                for (Place p : placeRepository.searchPlaces("ACTIVE", null, null, kw)) {
                    unique.putIfAbsent(p.getId(), p);
                    if (unique.size() >= 12) break;
                }
            }
        }

        // So khớp mềm không dấu trên toàn bộ địa điểm ACTIVE (tối ưu cho tên tiếng Việt không dấu)
        if (unique.size() < 8) {
            List<Place> allActive = placeRepository.findByStatus("ACTIVE");
            for (Place p : allActive) {
                String pNorm = normalize(safe(p.getName(), "") + " " + safe(p.getCity(), ""));
                if (!pNorm.isBlank() && (normCombined.contains(normalize(safe(p.getCity(), "___")))
                        || normCombined.contains(normalize(safe(p.getName(), "___"))))) {
                    unique.putIfAbsent(p.getId(), p);
                    if (unique.size() >= 12) break;
                }
            }
        }

        // Bổ sung Top địa điểm được đánh giá cao nhất nếu vẫn ít
        if (unique.size() < 6) {
            List<Place> top = placeRepository.findTopPlacesByRating(PageRequest.of(0, 8));
            for (Place p : top) {
                if ("ACTIVE".equalsIgnoreCase(p.getStatus())) {
                    unique.putIfAbsent(p.getId(), p);
                }
                if (unique.size() >= 10) break;
            }
        }

        return new ArrayList<>(unique.values());
    }

    private String buildCombinedText(String currentQuestion, List<ChatMessage> recentMessages) {
        StringBuilder sb = new StringBuilder();
        if (recentMessages != null) {
            for (ChatMessage m : recentMessages) {
                if (m.getContent() != null && "TEXT".equalsIgnoreCase(m.getMessageType())) {
                    sb.append(m.getContent()).append(" ");
                }
            }
        }
        if (currentQuestion != null) {
            sb.append(currentQuestion);
        }
        return sb.toString();
    }

    private String extractKeyword(String text) {
        if (text == null) return "";
        String cleaned = text.replaceAll("[\\p{Punct}]", " ").trim();
        String[] tokens = cleaned.split("\\s+");
        // Bỏ qua các từ dừng thông dụng để tìm từ khóa địa danh/món ăn tốt hơn
        Set<String> stop = Set.of("toi", "minh", "ban", "muon", "tao", "lich", "trinh", "di", "choi", "o", "tai",
                "xem", "sua", "xoa", "them", "cap", "nhat", "luu", "dia", "diem", "1", "2", "3", "4", "5", "ngay",
                "dem", "du", "lich", "wayfare", "tro", "ly", "khong", "co", "gi", "ngon", "nao", "nhung", "cac", "cua");
        List<String> kept = new ArrayList<>();
        for (String t : tokens) {
            if (!stop.contains(normalize(t)) && t.length() >= 2) {
                kept.add(t);
            }
        }
        if (kept.isEmpty()) return "";
        int size = kept.size();
        return size >= 2 ? kept.get(size - 2) + " " + kept.get(size - 1) : kept.get(0);
    }

    private String normalize(String input) {
        if (input == null) return "";
        String n = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .trim();
        return n;
    }

    private String safe(String val, String fallback) {
        return (val != null && !val.isBlank()) ? val.trim() : fallback;
    }
}
