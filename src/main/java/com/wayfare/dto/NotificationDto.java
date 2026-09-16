package com.wayfare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private Long id;
    private Long recipientId;
    private Long actorId;
    private String actorName;
    private String actorHandle;
    private String actorAvatar;
    private String type;
    private String message;
    private String targetUrl;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
