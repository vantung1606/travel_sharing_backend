package com.wayfare.common.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class HealthCheckController {

    @GetMapping({"/", "/health", "/api/health"})
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "UP");
        status.put("service", "Wayfare Travel Sharing Platform API");
        status.put("version", "1.0.0");
        status.put("environment", "production");
        status.put("timestamp", LocalDateTime.now().toString());
        status.put("message", "Wayfare Spring Boot 3 Backend is running smoothly.");
        return ResponseEntity.ok(status);
    }
}
