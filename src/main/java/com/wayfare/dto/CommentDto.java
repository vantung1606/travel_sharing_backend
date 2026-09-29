package com.wayfare.dto;

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
    private LocalDateTime createdAt;
    private String timeAgo;
}
