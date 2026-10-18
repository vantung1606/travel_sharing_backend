package com.wayfare.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
