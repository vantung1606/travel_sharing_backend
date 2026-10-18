package com.wayfare.modules.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayfare.exception.ResourceNotFoundException;
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

    @Value("${gemini.model:gemini-2.5-flash}")
    private String defaultModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiGenerateResponse generateContent(AiGenerateRequest request) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            log.error("AI Generation failed: GEMINI_API_KEY is not configured on server.");
            throw new ResourceNotFoundException("Tính năng AI hiện chưa khả dụng do máy chủ chưa cấu hình GEMINI_API_KEY.");
        }

        String targetModel = (request.getModel() != null && !request.getModel().isBlank())
                ? request.getModel().trim()
                : defaultModel;

        if (!targetModel.startsWith("gemini-") || targetModel.contains("1.5-flash")) {
            targetModel = defaultModel;
        }

        log.info("Executing AI Content Generation with model: {}", targetModel);

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
                    geminiApiKey
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

                    return AiGenerateResponse.builder()
                            .text(generatedText)
                            .model(targetModel)
                            .build();
                }
            }

            log.warn("Gemini API returned unexpected response: {}", response.getBody());
            throw new ResourceNotFoundException("Không nhận được kết quả hợp lệ từ mô hình AI.");
        } catch (Exception e) {
            log.error("Error communicating with Gemini API: {}", e.getMessage(), e);
            throw new ResourceNotFoundException("Lỗi khi xử lý với trí tuệ nhân tạo: " + e.getMessage());
        }
    }
}
