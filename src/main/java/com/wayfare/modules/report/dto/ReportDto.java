package com.wayfare.modules.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportDto {
    private Long id;
    private String targetType;
    private Long targetId;
    private String targetTitle;
    private Long reporterId;
    private String reporterName;
    private String reporterEmail;
    private String reporterAvatar;
    private String category;
    private String reason;
    private String description;
    private String evidenceImageUrl;
    private String status;
    private String moderatorNotes;
    private String actionTaken;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
