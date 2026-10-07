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
public class ReviewDto {
    private Long id;
    private Long placeId;
    private String placeName;
    private Long authorId;
    private String authorName;
    private String authorHandle;
    private String authorAvatar;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}
