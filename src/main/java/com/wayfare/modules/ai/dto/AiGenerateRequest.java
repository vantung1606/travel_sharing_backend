package com.wayfare.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGenerateRequest {
    @NotBlank(message = "Prompt không được để trống")
    private String prompt;

    private String systemPrompt;
    private String model;
    private Double temperature;
    private Integer maxTokens;
    private String responseMimeType;

    /**
     * Lịch sử hội thoại đa lượt: mỗi phần tử gồm {"role": "user"|"model", "text": "..."}
     */
    private List<Map<String, String>> history;
}
