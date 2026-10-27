package com.wayfare.modules.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportRequest {

    @NotBlank(message = "Loại đối tượng báo cáo không được để trống (POST, PLACE, USER, REVIEW)")
    private String targetType;

    @NotNull(message = "ID đối tượng báo cáo không được để trống")
    private Long targetId;

    @NotBlank(message = "Danh mục vi phạm không được để trống")
    private String category;

    @NotBlank(message = "Lý do báo cáo không được để trống")
    private String reason;

    private String description;

    private String evidenceImageUrl;
}
