package com.wayfare.modules.place.dto;

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
    private String placeCity;
    private Long authorId;
    private String authorName;
    private String authorHandle;
    private String authorEmail;
    private String authorAvatar;
    private Integer rating;
    private String comment;
    /** true = đang bị kiểm duyệt ẩn (chỉ Admin/Moderator thấy qua trang kiểm duyệt). */
    private Boolean isHidden;
    private LocalDateTime createdAt;
}
