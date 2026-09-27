package com.wayfare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminReportMetricsDto {
    private Long totalPostsToday;
    private Long pendingReportsCount;
    private Long hiddenPostsCount;
    private Double safeRate;
    private Integer growthPercent;
    private Long totalArticlesCount;
}
