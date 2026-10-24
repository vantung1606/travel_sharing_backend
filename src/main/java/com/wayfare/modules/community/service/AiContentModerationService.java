package com.wayfare.modules.community.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayfare.modules.ai.dto.AiGenerateRequest;
import com.wayfare.modules.ai.dto.AiGenerateResponse;
import com.wayfare.modules.ai.service.AiService;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class AiContentModerationService {

    private final AiService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong circuitTripTime = new AtomicLong(0);
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final long CIRCUIT_RESET_TIMEOUT_MS = 60_000; // 60s cooldown
    private static final long GEMINI_TIMEOUT_MS = 3500; // 3.5s timeout

    public AiContentModerationService(@Autowired(required = false) AiService aiService) {
        this.aiService = aiService;
        if (aiService != null && aiService.isApiKeyConfigured()) {
            log.info("AiContentModerationService initialized with Google Gemini LLM API active.");
        } else {
            log.info("AiContentModerationService initialized with Enhanced Heuristic Fallback engine.");
        }
    }

    @Getter
    @Builder
    public static class ModerationResult {
        private boolean isApproved;
        private int safetyScore; // 0 to 100
        private String category;
        private String flagReason;
        private String badgeText;
        private List<String> flaggedViolations;
        private String aiAnalysis; // Phân tích đa chiều từ Gemini
    }

    // System instruction (Skill) chuyên sâu cho Gemini phân tích đa góc độ
    private static final String GEMINI_MODERATION_SKILL = """
            Bạn là Chuyên gia Thẩm định & Kiểm duyệt Nội dung Trí tuệ Nhân tạo (WanderAI Smart Shield) của mạng xã hội du lịch Wayfare.
            Nhiệm vụ của bạn là phân tích sâu ngữ cảnh thực tế, sắc thái cảm xúc và tính chất của bài viết để đưa ra quyết định duyệt công bằng, nhân văn, hiểu văn hóa mạng xã hội Việt Nam và tuyệt đối không máy móc bắt bẻ từng từ khóa.

            BẮT BUỘC PHÂN TÍCH THEO 4 GÓC ĐỘ CỐT LÕI:
            1. Góc độ Bản chất Tài chính & Đòi nợ (Debt & Casual Humor):
               - NGUY HIỂM (Chặn / Flag): Tín dụng đen, bốc bát họ, cho vay nặng lãi, đe dọa bạo lực (cắt tai, tạt sơn, ném mắm tôm), đòi nợ giang hồ, khủng bố tinh thần, bóc phốt quỵt nợ nghiêm trọng nhằm triệt hạ danh dự cá nhân.
               - VÔ HẠI / ĐÙA VUI (Duyệt / Approved): Các bài đăng trêu đùa bạn bè, ảnh meme hài hước, nhắc nợ số tiền nhỏ (như đòi 10k, 20k ăn sáng, tiền ly trà sữa, đùa vui sau chuyến đi) có tính chất giải trí, thân thiện, gắn kết cộng đồng. Tuyệt đối không quá khắt khe với các tương tác vui này.

            2. Góc độ Dấu hiệu Lừa đảo & Chiếm đoạt tài sản (Scams & Fraud):
               - Bài viết có dấu hiệu lừa đảo, đa cấp, dụ dỗ đầu tư tài chính, tiền ảo, kêu gọi chuyển tiền mờ ám, việc nhẹ lương cao, cờ bạc tài xỉu, mạo danh lừa gạt cộng đồng hay không? Nếu có -> KHÔNG DUYỆT (approved = false, safetyScore < 50).

            3. Góc độ Mức độ ảnh hưởng diện rộng & Tác hại xã hội (Social Harm & Public Impact):
               - Bài viết có gây hại cho cộng đồng, kích động thù địch, hoang mang dư luận, phá hoại khu bảo tồn sinh thái / rừng cấm, hay ảnh hưởng tiêu cực diện rộng không? Nếu chỉ là status cá nhân, chia sẻ du lịch, trải nghiệm ẩm thực, meme đời sống vui vẻ -> DUYỆT (approved = true).

            4. Góc độ Ngôn từ thù ghét, thô tục & khiêu dâm (Toxicity & Harassment):
               - Có chửi bới xúc phạm nhân phẩm thậm tệ, đồi trụy hoặc quấy rối tình dục hay không?

            QUY TẮC CHO ĐIỂM & DUYỆT:
            - safetyScore: từ 0 đến 100.
              + Điểm >= 80: Duyệt tự động công khai (approved = true). Các bài chia sẻ du lịch, ẩm thực, hoặc bài đăng đùa vui / meme vô hại (như đòi 20k ăn sáng) nên cho điểm cao từ 85 - 98.
              + Điểm < 80: Giữ lại để Quản trị viên xem xét thủ công (approved = false).
            - category: Đặt tên chuyên mục phù hợp (ví dụ: "Ẩm thực & Check-in", "Meme & Đời sống", "Trải nghiệm & Khám phá", "Cảnh báo Lừa đảo / Tín dụng đen", v.v.).

            KẾT QUẢ ĐẦU RA BẮT BUỘC LÀ JSON THUẦN TÚY (không kèm markdown ```json ``` hay text thừa):
            {
              "approved": true,
              "safetyScore": 95,
              "category": "Ẩm thực & Check-in",
              "badgeText": "Nội dung an toàn",
              "flagReason": null,
              "analysis": "Tóm tắt 1-2 câu nhận định theo các góc độ (tính chất lừa đảo, mức độ ảnh hưởng, mục đích bài viết) và lý do duyệt."
            }
            """;

    // Danh sách từ khóa đòi nợ / tranh chấp tài chính / đe dọa
    private static final List<String> FINANCIAL_DISPUTE_VIOLATIONS = List.of(
            "đòi nợ", "doi no", "trả nợ", "tra no", "quỵt nợ", "quyt no", "bùng nợ", "bung no",
            "vay nợ", "vay no", "vay tiền", "vay tien", "mượn tiền", "muon tien", "mượn nợ", "muon no",
            "trốn nợ", "tron no", "siết nợ", "siet no", "xiết nợ", "xiet no", "thu hồi nợ", "thu hoi no",
            "con nợ", "con no", "chủ nợ", "chu no", "trả tiền đây", "tra tien day", "mau trả tiền", "mau tra tien",
            "bốc bát họ", "boc bat ho", "tín dụng đen", "tin dung den", "lãi ngày", "lai ngay", "lãi suất cao",
            "nợ tiền", "no tien", "thiếu nợ", "thieu no", "mắc nợ", "mac no", "đầu gấu đòi", "tạt sơn", "cắt tai",
            "ném mắm tôm", "nem mam tom", "tới nhà", "toi nha", "đến nhà", "den nha", "sang nhà", "sang nha",
            "quỵt tiền", "quyt tien", "bùng tiền", "bung tien", "bán nợ", "ban no", "vay nóng", "vay nong",
            "cho vay lãi", "nợ 45 triệu", "100 triệu", "triệu", "trieu"
    );

    // Mẫu trêu đùa đời sống vô hại (20k ăn sáng, trà sữa, cafe, ...)
    private static final List<String> HARMLESS_HUMOR_PATTERNS = List.of(
            "ăn sáng", "an sang", "trà sữa", "tra sua", "cafe", "cà phê", "bữa trưa", "bua trua",
            "tiền ăn", "tien an", "bữa sáng", "bua sang", "20k", "10k", "30k", "50k", "meme"
    );

    private static final List<String> ECO_SAFETY_VIOLATIONS = List.of(
            "cấm lửa", "đốt lửa", "lách kiểm lâm", "chốt kiểm lâm", "vượt rào", "chui rào",
            "vào rừng cấm", "săn bắt", "bắt thú", "bẻ san hô", "hái san hô", "tự ý cắm trại", "cắm trại chui"
    );

    private static final List<String> SCAM_GAMBLING_VIOLATIONS = List.of(
            "tiền ảo", "tien ao", "crypto", "telegram", "zalo 0", "zalo:", "kiếm tiền online", "kiem tien online",
            "hoa hồng cao", "cờ bạc", "co bac", "đánh bài", "danh bai", "kubet", "bet88", "tài xỉu", "tai xiu",
            "lô đề", "lo de", "đánh đề", "danh de", "soi cầu", "soi cau", "vay nóng", "vay nong"
    );

    private static final List<String> OFF_TOPIC_COMMERCIAL_VIOLATIONS = List.of(
            "bán đất", "ban dat", "bán nhà", "ban nha", "bất động sản", "bat dong san",
            "sim số đẹp", "sim so dep", "việc nhẹ lương cao", "viec nhe luong cao",
            "chữa bệnh trĩ", "chua benh tri", "chữa yếu sinh lý", "chua yeu sinh ly", "thuốc nam trị"
    );

    private static final List<String> DEFAMATION_TOXIC_VIOLATIONS = List.of(
            "chó má", "khốn nạn", "súc vật", "mất dạy", "vô học"
    );

    private String removeAccents(String src) {
        if (src == null) return "";
        String nfd = java.text.Normalizer.normalize(src, java.text.Normalizer.Form.NFD);
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfd).replaceAll("").replace('đ', 'd').replace('Đ', 'D').toLowerCase();
    }

    public ModerationResult moderate(String title, String content, String locationTag, String category) {
        return moderate(title, content, locationTag, category, false, false);
    }

    public ModerationResult moderate(String title, String content, String locationTag, String category, boolean hasAttachedItinerary) {
        return moderate(title, content, locationTag, category, hasAttachedItinerary, false);
    }

    public ModerationResult moderate(String title, String content, String locationTag, String category, boolean hasAttachedItinerary, boolean isSharedPost) {
        log.info("Running AI Content Moderation for post: '{}', Category: '{}', isShared: {}", title, category, isSharedPost);

        // 1. ƯU TIÊN HÀNG ĐẦU: Gọi Gemini AI API với giới hạn thời gian (Timeout 3.5s) & Circuit Breaker chống nghẽn
        if (aiService != null && aiService.isApiKeyConfigured() && isCircuitClosed()) {
            try {
                CompletableFuture<ModerationResult> future = CompletableFuture.supplyAsync(
                        () -> moderateWithGemini(title, content, locationTag, category)
                );
                ModerationResult geminiResult = future.get(GEMINI_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (geminiResult != null) {
                    recordSuccess();
                    return geminiResult;
                }
            } catch (TimeoutException te) {
                log.warn("Gemini AI moderation timed out (> {}ms). Tripping circuit breaker and falling back to Heuristic engine.", GEMINI_TIMEOUT_MS);
                recordFailure();
            } catch (Exception e) {
                log.warn("Gemini AI moderation execution failed ({}). Falling back to Heuristic engine.", e.getMessage());
                recordFailure();
            }
        }

        // 2. Dự phòng: Thuật toán Heuristic (phân biệt rạch ròi giữa đùa vui vô hại vs. đòi nợ / đe dọa)
        return moderateWithHeuristicFallback(title, content, locationTag, category, hasAttachedItinerary, isSharedPost);
    }

    private boolean isCircuitClosed() {
        if (consecutiveFailures.get() < MAX_CONSECUTIVE_FAILURES) {
            return true;
        }
        long elapsed = System.currentTimeMillis() - circuitTripTime.get();
        if (elapsed > CIRCUIT_RESET_TIMEOUT_MS) {
            log.info("Circuit breaker half-open: resetting consecutive failures to allow Gemini retry.");
            consecutiveFailures.set(0);
            return true;
        }
        log.warn("Gemini Circuit breaker is OPEN. Skipping remote call, defaulting to Heuristic instantly (0ms latency).");
        return false;
    }

    private void recordSuccess() {
        if (consecutiveFailures.get() > 0) {
            consecutiveFailures.set(0);
            log.info("Gemini moderation succeeded. Circuit breaker fully reset.");
        }
    }

    private void recordFailure() {
        int fails = consecutiveFailures.incrementAndGet();
        if (fails >= MAX_CONSECUTIVE_FAILURES) {
            circuitTripTime.set(System.currentTimeMillis());
            log.warn("Gemini moderation failed {} times consecutively. Circuit breaker OPENED for {}s. Bypassing Gemini to prevent user lag.",
                    fails, CIRCUIT_RESET_TIMEOUT_MS / 1000);
        }
    }

    private ModerationResult moderateWithGemini(String title, String content, String locationTag, String category) {
        try {
            String userPrompt = String.format("""
                    Hãy thẩm định bài viết mạng xã hội cộng đồng sau:
                    - Tiêu đề: "%s"
                    - Nội dung: "%s"
                    - Địa điểm: "%s"
                    - Chuyên mục: "%s"
                    """,
                    title != null ? title : "",
                    content != null ? content : "",
                    locationTag != null ? locationTag : "Việt Nam",
                    category != null ? category : "Ẩm thực & Check-in"
            );

            AiGenerateRequest request = AiGenerateRequest.builder()
                    .systemPrompt(GEMINI_MODERATION_SKILL)
                    .prompt(userPrompt)
                    .temperature(0.1)
                    .maxTokens(1024)
                    .responseMimeType("application/json")
                    .build();

            AiGenerateResponse response = aiService.generateContent(request);
            if (response != null && response.getText() != null && !response.getText().isBlank()) {
                String jsonText = response.getText().trim();
                if (jsonText.startsWith("```")) {
                    jsonText = jsonText.replaceAll("^```(json)?\\s*", "").replaceAll("\\s*```$", "").trim();
                }

                JsonNode root = objectMapper.readTree(jsonText);
                if (!root.has("approved")) {
                    log.warn("Gemini response missing 'approved' key: {}. Falling back to heuristic.", jsonText);
                    return null;
                }

                boolean approved = root.path("approved").asBoolean(false);
                int score = root.path("safetyScore").asInt(approved ? 90 : 30);
                if (score < 80) {
                    approved = false;
                }

                String cat = root.path("category").asText(category != null ? category : "Ẩm thực & Check-in");
                String badge = approved ? "Nội dung an toàn" : "Chờ Admin kiểm duyệt";
                String flagReason = root.hasNonNull("flagReason") && !root.path("flagReason").asText().isBlank()
                        ? root.path("flagReason").asText()
                        : (approved ? null : "Bài viết có dấu hiệu vi phạm an toàn, cần Admin kiểm duyệt.");
                String analysis = root.path("analysis").asText("");

                log.info("Gemini AI Content Moderation Successful -> Approved: {}, Score: {}/100, Analysis: {}",
                        approved, score, analysis);

                return ModerationResult.builder()
                        .isApproved(approved)
                        .safetyScore(score)
                        .category(cat)
                        .flagReason(flagReason)
                        .badgeText(badge)
                        .aiAnalysis(analysis)
                        .flaggedViolations(approved ? List.of() : List.of(flagReason))
                        .build();
            }
        } catch (Exception e) {
            log.warn("Gemini AI moderation call failed or timed out: {}. Using enhanced fallback engine.", e.getMessage());
        }
        return null;
    }

    private ModerationResult moderateWithHeuristicFallback(String title, String content, String locationTag, String category, boolean hasAttachedItinerary, boolean isSharedPost) {
        String rawCombined = ((title != null ? title : "") + " " + (content != null ? content : "") + " " + (locationTag != null ? locationTag : "")).toLowerCase();
        String unaccented = removeAccents(rawCombined);

        int score = 100;
        List<String> violations = new ArrayList<>();
        String detectedCategory = category != null && !category.isBlank() ? category : "Ẩm thực & Check-in";
        String mainReason = null;
        String analysis = "Hệ thống duyệt tự động qua bộ lọc an toàn dự phòng.";

        // 1. Kiểm tra từ khóa đòi nợ & tranh chấp tài chính
        boolean hasDebtWord = false;
        String matchedDebtWord = null;
        for (String debtKw : FINANCIAL_DISPUTE_VIOLATIONS) {
            String unaccentedKw = removeAccents(debtKw);
            if (rawCombined.contains(debtKw) || unaccented.contains(unaccentedKw)) {
                hasDebtWord = true;
                matchedDebtWord = debtKw;
                break;
            }
        }

        if (hasDebtWord) {
            // Kiểm tra xem có THỰC SỰ là đùa vui đời sống vô hại (ăn sáng, trà sữa, cafe và số tiền nhỏ) không
            boolean hasFoodJokePattern = false;
            for (String jokeKw : HARMLESS_HUMOR_PATTERNS) {
                if (rawCombined.contains(jokeKw) || unaccented.contains(removeAccents(jokeKw))) {
                    hasFoodJokePattern = true;
                    break;
                }
            }

            // Các từ đe dọa hoặc số tiền lớn TUYỆT ĐỐI KHÔNG ĐƯỢC COI LÀ ĐÙA VUI
            boolean hasThreatOrBigMoney = rawCombined.contains("tới nhà") || unaccented.contains("toi nha")
                    || rawCombined.contains("đến nhà") || unaccented.contains("den nha")
                    || rawCombined.contains("sang nhà") || unaccented.contains("sang nha")
                    || rawCombined.contains("cắt tai") || rawCombined.contains("tạt sơn")
                    || rawCombined.contains("triệu") || unaccented.contains("trieu")
                    || rawCombined.contains("trăm") || unaccented.contains("tram")
                    || rawCombined.contains("quỵt") || unaccented.contains("quyt")
                    || rawCombined.contains("bùng") || unaccented.contains("bung");

            if (hasFoodJokePattern && !hasThreatOrBigMoney) {
                score = 95;
                detectedCategory = "Ẩm thực & Check-in";
                analysis = "Bài viết mang tính chất trêu đùa hài hước vô hại trong cộng đồng (như đòi tiền ăn sáng/trà sữa), không có tính chất đe dọa hay tín dụng đen.";
            } else {
                score = 20; // < 80 => BẮT BUỘC CHUYỂN ADMIN DUYỆT!
                violations.add("Phát hiện nội dung đòi nợ hoặc tranh chấp tài chính cá nhân: '" + matchedDebtWord + "'");
                detectedCategory = "Đòi nợ & Tranh chấp Tài chính";
                mainReason = "Phát hiện nội dung có dấu hiệu đòi nợ hoặc đe dọa tài chính cá nhân (chứa từ khóa: '" + matchedDebtWord + "'). Hệ thống tạm giữ để Quản trị viên xem xét.";
                analysis = "Nội dung mang tính chất đòi nợ / đe dọa tài chính, không đạt tiêu chuẩn tự động xuất bản. Đã chuyển về hàng đợi Admin kiểm duyệt.";
            }
        }

        // 2. Kiểm tra dấu hiệu lừa đảo / cờ bạc
        for (String scamKw : SCAM_GAMBLING_VIOLATIONS) {
            String unaccentedKw = removeAccents(scamKw);
            if (rawCombined.contains(scamKw) || unaccented.contains(unaccentedKw)) {
                score -= 60;
                violations.add("Dấu hiệu spam hoặc lừa đảo tài chính: '" + scamKw + "'");
                if (mainReason == null) {
                    detectedCategory = "Spam & Lừa đảo tài chính";
                    mainReason = "Nội dung có dấu hiệu lừa đảo đa cấp, tiền ảo hoặc cờ bạc bất hợp pháp. Đã chuyển sang Admin duyệt thủ công.";
                }
            }
        }

        // 3. Kiểm tra rao vặt ngoài ngành du lịch
        for (String offTopicKw : OFF_TOPIC_COMMERCIAL_VIOLATIONS) {
            String unaccentedKw = removeAccents(offTopicKw);
            if (rawCombined.contains(offTopicKw) || unaccented.contains(unaccentedKw)) {
                score -= 55;
                violations.add("Rao vặt lạc đề ngoài ngành du lịch: '" + offTopicKw + "'");
                if (mainReason == null) {
                    detectedCategory = "Rao vặt ngoài ngành Du lịch";
                    mainReason = "Nội dung mang tính chất rao vặt thương mại, bất động sản hoặc sản phẩm ngoài ngành du lịch. Đã chuyển sang Admin duyệt.";
                }
            }
        }

        // 4. Kiểm tra phá hoại môi trường / an toàn
        for (String ecoKw : ECO_SAFETY_VIOLATIONS) {
            String unaccentedKw = removeAccents(ecoKw);
            if (rawCombined.contains(ecoKw) || unaccented.contains(unaccentedKw)) {
                score -= 40;
                violations.add("Vi phạm bảo tồn & an toàn sinh thái: '" + ecoKw + "'");
                if (mainReason == null) {
                    detectedCategory = "Nguy cơ an toàn & Bảo tồn";
                    mainReason = "Tuyên truyền hành vi xâm phạm rừng cấm hoặc cắm trại trái phép. Đã chuyển sang Admin duyệt.";
                }
            }
        }

        // 5. Ngôn từ độc hại thậm tệ
        for (String toxicKw : DEFAMATION_TOXIC_VIOLATIONS) {
            String unaccentedKw = removeAccents(toxicKw);
            if (rawCombined.contains(toxicKw) || unaccented.contains(unaccentedKw)) {
                score -= 45;
                violations.add("Ngôn từ công kích / thù ghét: '" + toxicKw + "'");
                if (mainReason == null) {
                    detectedCategory = "Ngôn từ độc hại & Bôi nhọ";
                    mainReason = "Bài viết chứa ngôn từ xúc phạm nghiêm trọng danh dự người khác. Đã chuyển sang Admin duyệt.";
                }
            }
        }

        score = Math.max(10, Math.min(100, score));
        boolean approved = score >= 80;
        String badge = approved ? "Nội dung an toàn" : "Chờ Admin kiểm duyệt";

        return ModerationResult.builder()
                .isApproved(approved)
                .safetyScore(score)
                .category(detectedCategory)
                .flagReason(approved ? null : mainReason)
                .badgeText(badge)
                .flaggedViolations(violations)
                .aiAnalysis(analysis)
                .build();
    }
}
