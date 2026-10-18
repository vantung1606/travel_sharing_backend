package com.wayfare.common.controller;

import com.wayfare.modules.ai.service.AiService;
import com.wayfare.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthCheckController {

    private final JwtTokenProvider jwtTokenProvider;
    private final AiService aiService;

    @GetMapping({"/", "/health", "/api/health"})
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "UP");
        status.put("service", "Wayfare Travel Sharing Platform API");
        status.put("version", "1.0.0");
        status.put("environment", "production");
        status.put("jwtConfigured", jwtTokenProvider.isSecretPersistent());
        status.put("jwtMode", jwtTokenProvider.isSecretPersistent() ? "PERSISTENT_ENV" : "EPHEMERAL_RUNTIME");
        status.put("aiConfigured", aiService.isApiKeyConfigured());
        status.put("timestamp", LocalDateTime.now().toString());
        status.put("message", "Wayfare Spring Boot 3 Backend is running smoothly.");
        return ResponseEntity.ok(status);
    }
}
