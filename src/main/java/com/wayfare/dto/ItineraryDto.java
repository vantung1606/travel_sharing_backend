package com.wayfare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItineraryDto {
    private Long id;
    private Long creatorId;
    private String creatorName;
    private String creatorEmail;
    private String creatorAvatar;
    private String title;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal budgetTotal;
    private String coverImageUrl;
    private Boolean isAiGenerated;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<ItineraryDetailDto> details = new ArrayList<>();

    @Builder.Default
    private List<ItineraryMemberDto> members = new ArrayList<>();

    @Builder.Default
    private List<ItineraryExpenseDto> expenses = new ArrayList<>();

    private BudgetSummaryDto budgetSummary;
}
