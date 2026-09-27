package com.wayfare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetSummaryDto {
    private Long itineraryId;
    private BigDecimal budgetTotal;
    private BigDecimal actualTotal;
    private BigDecimal remainingBudget;
    private Boolean isOverBudget;
    private Double spentPercentage;
    private Map<String, BigDecimal> breakdownByCategory;
}
