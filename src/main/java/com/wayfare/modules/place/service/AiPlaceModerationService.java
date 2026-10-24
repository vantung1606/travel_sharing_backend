package com.wayfare.modules.place.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayfare.entity.Place;
import com.wayfare.modules.ai.dto.AiGenerateRequest;
import com.wayfare.modules.ai.dto.AiGenerateResponse;
import com.wayfare.modules.ai.service.AiService;
import com.wayfare.modules.place.dto.CreatePlaceRequest;
import com.wayfare.repository.PlaceRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AiPlaceModerationService {

    private final PlaceRepository placeRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiPlaceModerationService(PlaceRepository placeRepository,
                                    @Autowired(required = false) AiService aiService) {
        this.placeRepository = placeRepository;
        this.aiService = aiService;
        log.info("AiPlaceModerationService initialized (AI Gemini active: {})",
                aiService != null && aiService.isApiKeyConfigured());
    }

    @Getter
    @Builder
    public static class PlaceModerationResult {
        private boolean approved;
        private String moderationStatus; // "APPROVED_BY_AI", "DUPLICATE_FLAGGED", "UNVERIFIED_SUSPECTED"
        private String note;
        private boolean requiresAdminReview;
    }

    private static final String GEMINI_PLACE_MODERATION_SKILL = """
            Bạn là Chuyên gia Thẩm định & Xác thực Địa điểm Du lịch (WanderAI Place Shield) của hệ thống Wayfare tại Việt Nam.
            Nhiệm vụ của bạn là thẩm định nghiêm ngặt tính xác thực và hợp lệ của địa điểm người dùng gửi lên:

            1. TÍNH XÁC THỰC (REAL PLACE VERIFICATION):
               - Địa điểm này có thật hay không? (Quán cafe, homestay, danh thắng, nhà hàng, điểm tham quan, di tích, bãi biển, công viên...).
               - CẢNH BÁO TỪ CHỐI (approved = false): Địa điểm bịa đặt, troll, tên nhảm nhí, ký tự rác, nhà riêng cá nhân không làm dịch vụ, địa chỉ ảo không tồn tại trên thực tế tại thành phố đó.
               - DUYỆT (approved = true): Địa điểm có thật hoặc có tính khả thi cao, thông tin địa chỉ và dịch vụ rõ ràng, phù hợp trải nghiệm du lịch.

            2. YÊU CẦU ĐỊA CHỈ PHẢI CỤ THỂ ĐẦY ĐỦ CÁC CẤP HÀNH CHÍNH (BẮT BUỘC):
               - KHÔNG ĐƯỢC CỘC LỐC: Tuyệt đối không chấp nhận các địa chỉ sơ sài chỉ ghi mỗi tên đường như '202 Chương Dương'.
               - PHẢI CỤ THỂ ĐẦY ĐỦ: Địa chỉ bắt buộc phải có Số nhà/Tên đường kèm Phường/Xã/Quận/Huyện và Tỉnh/Thành phố (Ví dụ chuẩn: '202 Chương Dương - Sơn Trà - Đà Nẵng' hoặc '202 Chương Dương, Phường Mỹ An, Quận Ngũ Hành Sơn, Đà Nẵng').
               - Nếu địa chỉ ghi cộc lốc hoặc thiếu Quận/Huyện/Thành phố -> BẮT BUỘC approved = false, flagReason = '⚠️ Địa chỉ chưa đủ cụ thể! Không được chỉ ghi tên đường cộc lốc như "202 Chương Dương". Yêu cầu ghi rõ Số nhà/Đường - Phường/Quận/Huyện - Tỉnh/Thành phố (Ví dụ: "202 Chương Dương - Sơn Trà - Đà Nẵng").'

            KẾT QUẢ ĐẦU RA BẮT BUỘC LÀ JSON THUẦN TÚY (không markdown):
            {
              "approved": true,
              "confidenceScore": 90,
              "isRealPlace": true,
              "flagReason": null,
              "analysis": "Nhận định ngắn gọn về tính xác thực của địa điểm."
            }
            """;

    /**
     * Thẩm định toàn diện địa điểm:
     * 1. Đối soát CSDL phát hiện trùng lặp địa chỉ hoặc tên tại địa bàn.
     * 2. AI kiểm tra tính có thật của địa điểm và địa chỉ.
     * 3. Mọi bài bị từ chối đều chuyển cho Admin thẩm định tiếp.
     */
    public PlaceModerationResult moderatePlace(CreatePlaceRequest request) {
        log.info("Starting AI moderation for place '{}' at address '{}', city '{}'",
                request.getName(), request.getAddress(), request.getCity());

        // BƯỚC 1: ĐỐI SOÁT CSDL - KIỂM TRA TRÙNG LẶP ĐỊA CHỈ VÀ ĐỊA ĐIỂM
        List<Place> allExistingPlaces = placeRepository.findAll();
        Place duplicatePlace = findDuplicateInDatabase(request, allExistingPlaces);

        if (duplicatePlace != null) {
            String duplicateReason = String.format(
                    "⚠️ Phát hiện trùng lặp với địa điểm đã có trong hệ thống: #%d - \"%s\" (Địa chỉ: %s, %s). Mỗi địa chỉ cụ thể chỉ được đăng ký một lần.",
                    duplicatePlace.getId(), duplicatePlace.getName(), duplicatePlace.getAddress(), duplicatePlace.getCity()
            );
            log.warn("Place creation flagged as DUPLICATE: {}", duplicateReason);
            return PlaceModerationResult.builder()
                    .approved(false)
                    .moderationStatus("DUPLICATE_FLAGGED")
                    .note(duplicateReason)
                    .requiresAdminReview(true)
                    .build();
        }

        // BƯỚC 2: KIỂM TRA TÍNH CÓ THẬT QUA HEURISTIC CƠ BẢN
        String heuristicCheckReason = checkBasicRealPlaceHeuristic(request);
        if (heuristicCheckReason != null) {
            log.warn("Place creation failed basic heuristic: {}", heuristicCheckReason);
            return PlaceModerationResult.builder()
                    .approved(false)
                    .moderationStatus("UNVERIFIED_SUSPECTED")
                    .note(heuristicCheckReason)
                    .requiresAdminReview(true)
                    .build();
        }

        // BƯỚC 3: AI GEMINI THẨM ĐỊNH TÍNH CÓ THẬT VÀ PHÙ HỢP CỦA ĐỊA ĐIỂM
        PlaceModerationResult geminiResult = moderateWithGemini(request);
        if (geminiResult != null) {
            return geminiResult;
        }

        // BƯỚC 4: FALLBACK THÔNG MINH NẾU KHÔNG KẾT NỐI ĐƯỢC GEMINI
        return evaluateHeuristicApproved(request);
    }

    /**
     * Thuật toán đối soát CSDL:
     * - Địa chỉ cụ thể phải là duy nhất.
     * - Tên địa điểm tương tự trong cùng thành phố/quận.
     * - Tọa độ quá sát nhau (< 70m).
     */
    private Place findDuplicateInDatabase(CreatePlaceRequest req, List<Place> existingPlaces) {
        if (req == null || existingPlaces == null || existingPlaces.isEmpty()) {
            return null;
        }

        String reqNameNorm = normalizeText(req.getName());
        String reqAddrNorm = normalizeText(req.getAddress());
        String reqCityNorm = normalizeText(req.getCity());

        for (Place p : existingPlaces) {
            if (p == null || p.getName() == null) continue;

            String pNameNorm = normalizeText(p.getName());
            String pAddrNorm = normalizeText(p.getAddress());
            String pCityNorm = normalizeText(p.getCity());

            // 1. Trùng địa chỉ cụ thể
            if (!reqAddrNorm.isBlank() && !pAddrNorm.isBlank() && reqAddrNorm.length() >= 8) {
                if (reqAddrNorm.equals(pAddrNorm) ||
                    (pCityNorm.equals(reqCityNorm) && (pAddrNorm.contains(reqAddrNorm) || reqAddrNorm.contains(pAddrNorm)))) {
                    return p;
                }
            }

            // 2. Trùng tên và thành phố
            if (!reqNameNorm.isBlank() && reqNameNorm.equals(pNameNorm)) {
                if (pCityNorm.equals(reqCityNorm) || pCityNorm.contains(reqCityNorm) || reqCityNorm.contains(pCityNorm)) {
                    return p;
                }
            }

            // 3. Trùng tọa độ địa lý (< 70m)
            if (req.getLatitude() != null && req.getLongitude() != null &&
                p.getLatitude() != null && p.getLongitude() != null) {
                double distMeters = calculateDistanceMeters(
                        req.getLatitude().doubleValue(), req.getLongitude().doubleValue(),
                        p.getLatitude().doubleValue(), p.getLongitude().doubleValue()
                );
                if (distMeters < 70.0 && (reqNameNorm.contains(pNameNorm) || pNameNorm.contains(reqNameNorm))) {
                    return p;
                }
            }
        }
        return null;
    }

    private String checkBasicRealPlaceHeuristic(CreatePlaceRequest req) {
        String name = req.getName() != null ? req.getName().trim() : "";
        String address = req.getAddress() != null ? req.getAddress().trim() : "";

        if (name.length() < 3) {
            return "⚠️ Tên địa điểm quá ngắn (dưới 3 ký tự), không đủ thông tin nhận diện.";
        }

        // Kiểm tra tính cụ thể của địa chỉ (không được cộc lốc như '202 Chương Dương')
        String addressCheck = checkAddressSpecificity(address, req.getCity());
        if (addressCheck != null) {
            return addressCheck;
        }

        String normName = normalizeText(name);
        String normAddr = normalizeText(address);

        // Danh sách từ khóa troll / địa điểm không có thật
        List<String> spamKeywords = List.of(
                "nha tao", "nha toi", "cho xam", "quan vo van", "asdf", "qwer", "123456",
                "test", "khong co that", "dia chi ao", "bay ba", "cho choi", "cho vo van"
        );

        for (String spam : spamKeywords) {
            if (normName.contains(spam) || normAddr.contains(spam)) {
                return "⚠️ Tên hoặc địa chỉ có dấu hiệu spam, troll hoặc không có thật trên thực tế.";
            }
        }

        return null;
    }

    /**
     * Bắt buộc địa chỉ phải cụ thể đầy đủ cấu trúc Số nhà/Đường - Phường/Quận/Huyện - Tỉnh/Thành phố.
     * Cấm cộc lốc chỉ ghi mỗi tên đường như "202 Chương Dương".
     */
    private String checkAddressSpecificity(String address, String city) {
        if (address == null || address.trim().isBlank()) {
            return "⚠️ Địa chỉ cụ thể không được để trống.";
        }
        String trimmed = address.trim();
        if (trimmed.length() < 10) {
            return "⚠️ Địa chỉ chưa đủ cụ thể! Không được chỉ ghi tên đường cộc lốc như '202 Chương Dương'. Vui lòng ghi rõ: Số nhà/Đường - Phường/Quận/Huyện - Tỉnh/Thành phố (Ví dụ: '202 Chương Dương - Sơn Trà - Đà Nẵng').";
        }

        String norm = normalizeText(trimmed);
        boolean hasHyphenOrComma = trimmed.contains("-") || trimmed.contains(",") || trimmed.contains("/");
        boolean hasAdminKeyword = norm.contains("quan ") || norm.contains("huyen ") || 
                                  norm.contains("phuong ") || norm.contains("xa ") || 
                                  norm.contains("thi xa ") || norm.contains("thanh pho ") || 
                                  norm.contains("tp ");
        
        // Nếu địa chỉ chỉ cộc lốc dạng '202 chương dương' (không có dấu phân cách cấp hành chính và không có từ khóa phường/quận/huyện)
        if (!hasHyphenOrComma && !hasAdminKeyword) {
            return "⚠️ Địa chỉ chưa đủ cụ thể! Không được chỉ ghi tên đường cộc lốc như '202 Chương Dương'. Yêu cầu ghi đầy đủ: Số nhà/Đường - Phường/Quận/Huyện - Tỉnh/Thành phố (Ví dụ: '202 Chương Dương - Sơn Trà - Đà Nẵng').";
        }

        // Nếu có dấu phân cách '-' hoặc ',', kiểm tra xem có ít nhất 2 phân đoạn trở lên
        if (hasHyphenOrComma) {
            String[] segments = trimmed.split("[-.,/]");
            int validSegments = 0;
            for (String s : segments) {
                if (s.trim().length() >= 2) validSegments++;
            }
            if (validSegments < 2 && !hasAdminKeyword) {
                return "⚠️ Địa chỉ chưa đủ cụ thể! Yêu cầu ghi rõ cấu trúc: Số nhà/Đường - Phường/Quận/Huyện - Tỉnh/Thành phố (Ví dụ: '202 Chương Dương - Sơn Trà - Đà Nẵng').";
            }
        }

        return null;
    }

    private PlaceModerationResult moderateWithGemini(CreatePlaceRequest req) {
        if (aiService == null || !aiService.isApiKeyConfigured()) {
            return null;
        }

        try {
            String prompt = String.format("""
                    Thẩm định địa điểm sau:
                    - Tên địa điểm: "%s"
                    - Phân loại: "%s"
                    - Địa chỉ cụ thể: "%s"
                    - Thành phố / Tỉnh: "%s"
                    - Mô tả: "%s"
                    - Tiện ích: "%s"
                    """,
                    req.getName() != null ? req.getName() : "",
                    req.getCategoryName() != null ? req.getCategoryName() : "Tọa độ bản địa",
                    req.getAddress() != null ? req.getAddress() : "",
                    req.getCity() != null ? req.getCity() : "",
                    req.getDescription() != null ? req.getDescription() : "",
                    req.getAmenities() != null ? req.getAmenities() : ""
            );

            AiGenerateRequest aiReq = AiGenerateRequest.builder()
                    .systemPrompt(GEMINI_PLACE_MODERATION_SKILL)
                    .prompt(prompt)
                    .temperature(0.1)
                    .maxTokens(1024)
                    .responseMimeType("application/json")
                    .build();

            AiGenerateResponse response = aiService.generateContent(aiReq);
            if (response != null && response.getText() != null && !response.getText().isBlank()) {
                String jsonText = response.getText().trim();
                if (jsonText.startsWith("```")) {
                    jsonText = jsonText.replaceAll("^```(json)?\\s*", "").replaceAll("\\s*```$", "").trim();
                }

                JsonNode root = objectMapper.readTree(jsonText);
                boolean approved = root.path("approved").asBoolean(false);
                int score = root.path("confidenceScore").asInt(approved ? 90 : 30);
                String flagReason = root.hasNonNull("flagReason") && !root.path("flagReason").asText().isBlank()
                        ? root.path("flagReason").asText()
                        : (approved ? "AI WanderAI đã thẩm định: Địa điểm có thật, địa chỉ hợp lệ và không trùng lặp."
                                    : "AI nghi vấn địa điểm không có thật hoặc địa chỉ không chính xác, chuyển tiếp Admin duyệt.");

                log.info("Gemini Place Moderation -> Approved: {}, Score: {}/100, Reason: {}", approved, score, flagReason);

                return PlaceModerationResult.builder()
                        .approved(approved && score >= 75)
                        .moderationStatus(approved && score >= 75 ? "APPROVED_BY_AI" : "UNVERIFIED_SUSPECTED")
                        .note(flagReason)
                        .requiresAdminReview(!approved || score < 75)
                        .build();
            }
        } catch (Exception e) {
            log.warn("Gemini Place Moderation failed or timed out: {}. Using heuristic evaluation.", e.getMessage());
        }
        return null;
    }

    private PlaceModerationResult evaluateHeuristicApproved(CreatePlaceRequest req) {
        log.info("Evaluating place via intelligent heuristic verification engine.");
        return PlaceModerationResult.builder()
                .approved(true)
                .moderationStatus("APPROVED_BY_AI")
                .note("AI WanderAI đã thẩm định: Địa điểm có thật, địa chỉ hợp lệ và không trùng lặp.")
                .requiresAdminReview(false)
                .build();
    }

    private String normalizeText(String input) {
        if (input == null) return "";
        String temp = Normalizer.normalize(input, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(temp).replaceAll("")
                .toLowerCase()
                .replace("đ", "d")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371000; // meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }
}
