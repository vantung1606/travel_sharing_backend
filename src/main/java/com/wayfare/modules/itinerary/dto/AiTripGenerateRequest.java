package com.wayfare.modules.itinerary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiTripGenerateRequest {
    private String destination;
    private String budget;
    private String style;
    private String duration;
    private BigDecimal budgetAmount;
}

