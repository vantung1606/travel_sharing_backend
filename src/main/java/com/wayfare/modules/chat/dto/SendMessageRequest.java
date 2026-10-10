package com.wayfare.modules.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {
    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    private String content;

    @Builder.Default
    private String messageType = "TEXT"; // TEXT, IMAGE, VIDEO, LOCATION
}
