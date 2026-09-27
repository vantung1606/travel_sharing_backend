package com.wayfare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItineraryExpenseDto {
    private Long id;
    private Long itineraryId;
    private Long payerId;
    private String payerName;
    private BigDecimal amount;
    private String category;
    private String description;
    private String receiptImageUrl;
    private LocalDateTime createdAt;
}
