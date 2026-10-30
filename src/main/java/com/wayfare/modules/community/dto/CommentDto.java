package com.wayfare.modules.community.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentDto {
    private Long id;
    private Long postId;
    private Long authorId;
    private String authorName;
    private String authorHandle;
    private String authorAvatar;
    private String content;
    private Long parentId;
    private Long replyToUserId;
    private String replyToUserName;
    private String replyToUserHandle;
    @Builder.Default
    private java.util.List<CommentDto> replies = new java.util.ArrayList<>();
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "Asia/Ho_Chi_Minh")
    private java.time.ZonedDateTime createdAt;
    private String timeAgo;
}

