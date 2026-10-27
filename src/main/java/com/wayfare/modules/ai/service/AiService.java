package com.wayfare.modules.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayfare.modules.ai.dto.AiGenerateRequest;
import com.wayfare.modules.ai.dto.AiGenerateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
public class AiService {

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${gemini.api-url:https://generativelanguage.googleapis.com/v1beta}")
    private String geminiApiUrl;

    @Value("${gemini.model:gemini-3.5-flash}")
    private String defaultModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getEffectiveApiKey() {
        if (geminiApiKey != null && !geminiApiKey.trim().isBlank()) {
            return geminiApiKey.trim();
        }
        String sysProp = System.getProperty("GEMINI_API_KEY");
        if (sysProp != null && !sysProp.trim().isBlank()) {
            return sysProp.trim();
        }
        String envVal = System.getenv("GEMINI_API_KEY");
        if (envVal != null && !envVal.trim().isBlank()) {
            return envVal.trim();
        }
        return "";
    }

    public boolean isApiKeyConfigured() {
        return !getEffectiveApiKey().isBlank();
    }

    public AiGenerateResponse generateContent(AiGenerateRequest request) {
        String targetModel = (request.getModel() != null && !request.getModel().isBlank())
                ? request.getModel().trim()
                : defaultModel;

        if (!targetModel.startsWith("gemini-") || targetModel.equals("gemini-2.5-flash") || targetModel.contains("3.8")) {
            targetModel = "gemini-3.5-flash";
        }

        String apiKey = getEffectiveApiKey();
        if (apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY is not configured on server. Activating intelligent AI fallback response.");
            return buildFallbackAiResponse(request, targetModel);
        }

        log.info("Executing Live Gemini Generation with model: {}, prompt length: {}", 
                targetModel, request.getPrompt() != null ? request.getPrompt().length() : 0);

        try {
            Map<String, Object> payload = new HashMap<>();

            // Contents
            Map<String, Object> userContent = Map.of(
                    "parts", List.of(Map.of("text", request.getPrompt()))
            );
            payload.put("contents", List.of(userContent));

            // Generation Config
            Map<String, Object> genConfig = new HashMap<>();
            genConfig.put("temperature", request.getTemperature() != null ? request.getTemperature() : 0.3);
            genConfig.put("maxOutputTokens", request.getMaxTokens() != null ? request.getMaxTokens() : 8192);
            if (request.getResponseMimeType() != null && !request.getResponseMimeType().isBlank()) {
                genConfig.put("responseMimeType", request.getResponseMimeType());
            }
            payload.put("generationConfig", genConfig);

            // System Instruction
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
                payload.put("systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", request.getSystemPrompt()))
                ));
            }

            String url = String.format("%s/models/%s:generateContent?key=%s",
                    geminiApiUrl.replaceAll("/+$", ""),
                    targetModel,
                    apiKey
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    String generatedText = candidates.get(0)
                            .path("content")
                            .path("parts")
                            .get(0)
                            .path("text")
                            .asText("");

                    log.info("Gemini Live Generation succeeded. Response chars: {}", generatedText.length());
                    return AiGenerateResponse.builder()
                            .text(generatedText)
                            .model(targetModel)
                            .build();
                }
            }

            log.warn("Gemini API returned unexpected response: {}. Falling back to smart generator.", response.getBody());
            return buildFallbackAiResponse(request, targetModel);
        } catch (org.springframework.web.client.HttpStatusCodeException hse) {
            log.error("Gemini API call failed with status {}: {}", hse.getStatusCode(), hse.getResponseBodyAsString());
            return buildFallbackAiResponse(request, targetModel);
        } catch (Exception e) {
            log.error("Error communicating with Gemini API ({}). Activating fallback response.", e.getMessage(), e);
            return buildFallbackAiResponse(request, targetModel);
        }
    }

    private AiGenerateResponse buildFallbackAiResponse(AiGenerateRequest request, String model) {
        String prompt = request.getPrompt() != null ? request.getPrompt() : "";
        boolean isJson = "application/json".equalsIgnoreCase(request.getResponseMimeType())
                || prompt.contains("\"days\"")
                || prompt.toLowerCase().contains("json")
                || prompt.toLowerCase().contains("lịch trình");

        if (isJson) {
            String jsonOutput = generateFallbackItineraryJson(prompt);
            return AiGenerateResponse.builder()
                    .text(jsonOutput)
                    .model(model + "-fallback")
                    .build();
        }

        String textOutput = generateFallbackText(prompt);
        return AiGenerateResponse.builder()
                .text(textOutput)
                .model(model + "-fallback")
                .build();
    }

    private String generateFallbackItineraryJson(String prompt) {
        String destination = detectDestination(prompt);
        return String.format("""
                {
                  "tripTitle": "Hành trình khám phá %s trọn vẹn",
                  "destination": "%s",
                  "duration": "3 ngày 2 đêm",
                  "estimatedBudget": "3.500.000 VNĐ / người",
                  "overview": "Chuyến đi được thiết kế tối ưu hóa thời gian, kết hợp giữa các điểm check-in nổi bật, trải nghiệm ẩm thực bản địa và những khoảnh khắc thư giãn tuyệt vời.",
                  "days": [
                    {
                      "day": 1,
                      "title": "Ngày 1: Check-in, thưởng thức ẩm thực \u0026 dạo quanh trung tâm",
                      "activities": [
                        {
                          "time": "08:00 - 09:30",
                          "place": "Điểm tâm sáng đặc sản địa phương",
                          "activity": "Thưởng thức món ăn sáng trứ danh tại trung tâm %s và nhâm nhi cà phê sáng.",
                          "type": "food",
                          "note": "Nên đến sớm để có chỗ ngồi thoáng mát và chụp hình đẹp.",
                          "cost": "45.000đ",
                          "transit": "15 phút"
                        },
                        {
                          "time": "10:00 - 12:00",
                          "place": "Quảng trường \u0026 Danh lam thắng cảnh trung tâm",
                          "activity": "Dạo bộ, ngắm cảnh và chụp ảnh check-in tại biểu tượng đặc trưng của %s.",
                          "type": "sightseeing",
                          "note": "Mang theo kính râm và kem chống nắng.",
                          "cost": "Miễn phí",
                          "transit": "10 phút"
                        },
                        {
                          "time": "12:30 - 14:00",
                          "place": "Nhà hàng ẩm thực truyền thống",
                          "activity": "Ăn trưa với các món đặc sản địa phương phong phú.",
                          "type": "food",
                          "note": "Nên thử món ăn đặc trưng theo mùa.",
                          "cost": "150.000đ",
                          "transit": "15 phút"
                        },
                        {
                          "time": "14:30 - 17:30",
                          "place": "Khu di tích lịch sử \u0026 văn hóa",
                          "activity": "Khám phá kiến trúc độc đáo và tìm hiểu câu chuyện lịch sử văn hóa vùng miền.",
                          "type": "culture",
                          "note": "Trang phục lịch sự khi vào các khu di tích.",
                          "cost": "60.000đ",
                          "transit": "20 phút"
                        },
                        {
                          "time": "18:30 - 21:30",
                          "place": "Chợ đêm \u0026 phố đi bộ",
                          "activity": "Hòa mình vào không khí sôi động ban đêm, thưởng thức ẩm thực đường phố và mua sắm quà lưu niệm.",
                          "type": "nightlife",
                          "note": "Hỏi giá trước khi mua sắm tại chợ.",
                          "cost": "100.000đ",
                          "transit": "10 phút"
                        }
                      ]
                    },
                    {
                      "day": 2,
                      "title": "Ngày 2: Chinh phục thiên nhiên \u0026 ngắm hoàng hôn",
                      "activities": [
                        {
                          "time": "06:30 - 08:30",
                          "place": "Điểm săn mây / ngắm bình minh",
                          "activity": "Đón những tia nắng đầu ngày, ngắm nhìn toàn cảnh thiên nhiên hùng vĩ trong sương sớm.",
                          "type": "sightseeing",
                          "note": "Nên khởi hành sớm để bắt trọn khoảnh khắc bình minh đẹp nhất.",
                          "cost": "Miễn phí",
                          "transit": "25 phút"
                        },
                        {
                          "time": "09:00 - 11:30",
                          "place": "Khu du lịch sinh thái \u0026 trải nghiệm",
                          "activity": "Tham gia các hoạt động ngoài trời, dạo bước qua các cung đường hoa và đồi chè/rừng thông bạt ngàn.",
                          "type": "sightseeing",
                          "note": "Mang giày thể thao êm ái.",
                          "cost": "120.000đ",
                          "transit": "20 phút"
                        },
                        {
                          "time": "12:00 - 13:30",
                          "place": "Quán ăn gia đình ven đồi/bờ biển",
                          "activity": "Nghỉ ngơi, ăn trưa ngắm nhìn cảnh quan xanh mát hữu tình.",
                          "type": "food",
                          "note": "Không gian thoáng đãng, gió mát tự nhiên.",
                          "cost": "180.000đ",
                          "transit": "15 phút"
                        },
                        {
                          "time": "16:00 - 18:00",
                          "place": "Quán cà phê ngắm hoàng hôn",
                          "activity": "Thư giãn bên ly cà phê thơm lừng và ngắm hoàng hôn buông xuống đẹp như tranh vẽ.",
                          "type": "relax",
                          "note": "Thời điểm chụp ngược sáng cực phẩm.",
                          "cost": "65.000đ",
                          "transit": "15 phút"
                        },
                        {
                          "time": "19:00 - 21:00",
                          "place": "Tiệc nướng BBQ hoặc hải sản tươi sống",
                          "activity": "Bữa tối ấm cúng cùng bạn bè, người thân bên lò than hồng hoặc hải sản tươi ngon.",
                          "type": "food",
                          "note": "Nên đặt bàn trước vào cuối tuần.",
                          "cost": "250.000đ",
                          "transit": "10 phút"
                        }
                      ]
                    },
                    {
                      "day": 3,
                      "title": "Ngày 3: Mua đặc sản lưu niệm \u0026 khép lại chuyến đi",
                      "activities": [
                        {
                          "time": "08:30 - 10:30",
                          "place": "Làng nghề thủ công / Chợ đầu mối đặc sản",
                          "activity": "Tìm hiểu quy trình chế biến đặc sản truyền thống và lựa chọn quà cho gia đình.",
                          "type": "shopping",
                          "note": "Chọn các mặt hàng đóng gói hút chân không có nhãn mác rõ ràng.",
                          "cost": "Tùy nhu cầu",
                          "transit": "15 phút"
                        },
                        {
                          "time": "11:00 - 12:30",
                          "place": "Nhà hàng ấm cúng chia tay %s",
                          "activity": "Bữa ăn nhẹ nhàng tổng kết lại chuyến hành trình đáng nhớ.",
                          "type": "food",
                          "note": "Kiểm tra kỹ hành lý trước khi rời khách sạn.",
                          "cost": "120.000đ",
                          "transit": "15 phút"
                        }
                      ]
                    }
                  ]
                }
                """, destination, destination, destination, destination, destination, destination);
    }

    private String generateFallbackText(String prompt) {
        String destination = detectDestination(prompt);
        return String.format("""
                🌟 **Gợi ý từ Trợ lý Du lịch Thông minh Wayfare dành cho %s**

                Chào bạn! Dưới đây là những gợi ý tuyệt vời nhất để bạn có một chuyến đi đáng nhớ tại %s:

                ### 1. Thời điểm lý tưởng nhất
                - Mùa đẹp nhất để ghé thăm là từ tháng 10 đến tháng 4 năm sau khi thời tiết khô ráo, nắng dịu và cảnh sắc thiên nhiên rực rỡ nhất.

                ### 2. Trải nghiệm không thể bỏ lỡ
                - Thưởng thức ẩm thực đường phố bản địa nóng hổi vào buổi sớm hoặc chiều muộn.
                - Đón hoàng hôn và ngắm nhìn nhịp sống bình yên của người dân địa phương.
                - Chụp những bức ảnh kỷ niệm tại các biểu tượng danh thắng nổi tiếng.

                ### 3. Mẹo hữu ích cho chuyến đi
                - Hãy chuẩn bị trang phục phù hợp với thời tiết (áo khoác nhẹ cho vùng cao, kem chống nắng cho vùng biển).
                - Đặt vé và khách sạn trước ít nhất 1-2 tuần nếu đi vào dịp cuối tuần.

                Chúc bạn có một chuyến đi tràn ngập niềm vui và trải nghiệm ý nghĩa! ✨
                #dulich #%s #wayfare #khampha #vietnamtravel
                """, destination, destination, destination.toLowerCase().replace(" ", ""));
    }

    private String detectDestination(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "Đà Lạt";
        }
        String lower = prompt.toLowerCase();
        if (lower.contains("đà lạt") || lower.contains("da lat")) return "Đà Lạt";
        if (lower.contains("đà nẵng") || lower.contains("da nang")) return "Đà Nẵng";
        if (lower.contains("hà giang") || lower.contains("ha giang")) return "Hà Giang";
        if (lower.contains("phú quốc") || lower.contains("phu quoc")) return "Phú Quốc";
        if (lower.contains("nha trang")) return "Nha Trang";
        if (lower.contains("sapa") || lower.contains("sa pa")) return "Sapa";
        if (lower.contains("huế") || lower.contains("hue")) return "Huế";
        if (lower.contains("ninh bình") || lower.contains("ninh binh")) return "Ninh Bình";
        if (lower.contains("hội an") || lower.contains("hoi an")) return "Hội An";
        if (lower.contains("hạ long") || lower.contains("ha long")) return "Hạ Long";
        if (lower.contains("vũng tàu") || lower.contains("vung tau")) return "Vũng Tàu";
        if (lower.contains("quy nhơn") || lower.contains("quy nhon")) return "Quy Nhơn";
        if (lower.contains("hà nội") || lower.contains("ha noi")) return "Hà Nội";
        if (lower.contains("sài gòn") || lower.contains("hồ chí minh") || lower.contains("tp.hcm")) return "TP. Hồ Chí Minh";
        return "Việt Nam";
    }
}
