package com.wayfare.modules.community.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePostRequest {
    @NotBlank(message = "Nội dung bài viết không được để trống")
    private String content;

    private String title;
    private String locationTag;
    private String imageUrl;
    private List<String> images;
    private String videoUrl;
    private String category;
    private Long itineraryId;
    private String visibility; // PUBLIC, PRIVATE
}

