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
import org.springframework.security.core.Authentication;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final Map<String, Deque<Long>> rateLimitMap = new ConcurrentHashMap<>();

    private static final int MAX_USER_REQUESTS_PER_MINUTE = 30;
    private static final int MAX_ANONYMOUS_REQUESTS_PER_MINUTE = 10;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<AiGenerateResponse>> generateContent(
            @Valid @RequestBody AiGenerateRequest request,
            HttpServletRequest servletRequest,
            Authentication authentication) {
        String rateKey;
        int maxAllowed;

        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            rateKey = "USER:" + authentication.getName().toLowerCase().trim();
            maxAllowed = MAX_USER_REQUESTS_PER_MINUTE;
        } else {
            rateKey = "IP:" + resolveClientIp(servletRequest);
            maxAllowed = MAX_ANONYMOUS_REQUESTS_PER_MINUTE;
        }

        if (isRateLimited(rateKey, maxAllowed)) {
            log.warn("Rate limit exceeded for AI generation (key={})", rateKey);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Bạn đã vượt quá giới hạn gọi AI (" + maxAllowed + " req/phút). Vui lòng thử lại sau giây lát."));
        }

        log.info("REST request to generate AI content (rateKey={}, model={})", rateKey, request.getModel());
        AiGenerateResponse response = aiService.generateContent(request);
        return ResponseEntity.ok(ApiResponse.success("Tạo nội dung AI thành công", response));
    }

    private boolean isRateLimited(String key, int maxAllowed) {
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;
        Deque<Long> timestamps = rateLimitMap.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxAllowed) {
                return true;
            }
            timestamps.addLast(now);
            return false;
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        // Socket remote address cannot be forged by HTTP client request headers
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr.trim() : "unknown";
    }
}
