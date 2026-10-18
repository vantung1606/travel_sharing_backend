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

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AiController {

    private final AiService aiService;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<AiGenerateResponse>> generateContent(
            @Valid @RequestBody AiGenerateRequest request) {
        log.info("REST request to generate AI content, model: {}", request.getModel());
        AiGenerateResponse response = aiService.generateContent(request);
        return ResponseEntity.ok(ApiResponse.success("Tạo nội dung AI thành công", response));
    }
}
