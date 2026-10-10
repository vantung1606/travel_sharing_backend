package com.wayfare.modules.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    private Long id;
    private Long roomId;
    private Long senderId;
    private String senderEmail;
    private String senderName;
    private String senderHandle;
    private String senderAvatar;
    private String content;
    private String messageType; // TEXT, IMAGE, VIDEO, LOCATION, SYSTEM
    private String time;
    private LocalDateTime createdAt;
    private Boolean isMe;
}
