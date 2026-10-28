package com.wayfare.modules.itinerary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReorderItineraryDetailRequest {
    private Long detailId;
    private Integer visitOrder;
    private Integer dayNumber;
}
