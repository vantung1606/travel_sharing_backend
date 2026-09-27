package com.wayfare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItineraryDetailDto {
    private Long id;
    private Long itineraryId;
    private Long placeId;
    private String locationName;
    private String locationAddress;
    private String category;
    private Integer dayNumber;
    private Integer visitOrder;
    private LocalTime startTime;
    private BigDecimal estimatedCost;
    private String aiTip;
    private String transitInfo;
    private String note;
}
