package com.wayfare.modules.ai.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.modules.ai.dto.AiGenerateRequest;
import com.wayfare.modules.ai.dto.AiGenerateResponse;
import com.wayfare.modules.ai.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final Map<String, Deque<Long>> rateLimitMap = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS_PER_MINUTE = 20;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<AiGenerateResponse>> generateContent(
            @Valid @RequestBody AiGenerateRequest request,
            HttpServletRequest servletRequest) {
        String clientIp = resolveClientIp(servletRequest);

        if (isRateLimited(clientIp)) {
            log.warn("Rate limit exceeded for AI generation from IP: {}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Bạn đã vượt quá giới hạn gọi AI (tối đa 20 yêu cầu/phút). Vui lòng thử lại sau giây lát."));
        }

        log.info("REST request to generate AI content from IP={}, model: {}", clientIp, request.getModel());
        AiGenerateResponse response = aiService.generateContent(request);
        return ResponseEntity.ok(ApiResponse.success("Tạo nội dung AI thành công", response));
    }

    private boolean isRateLimited(String clientIp) {
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;
        Deque<Long> timestamps = rateLimitMap.computeIfAbsent(clientIp, k -> new ConcurrentLinkedDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
                return true;
            }
            timestamps.addLast(now);
            return false;
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }
}
