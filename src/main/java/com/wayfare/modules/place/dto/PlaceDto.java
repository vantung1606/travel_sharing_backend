package com.wayfare.modules.place.dto;

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
public class PlaceDto {
    private Long id;
    private String name;
    private String description;
    private String address;
    private String city;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal ticketPrice;
    private BigDecimal averageRating;
    private Integer reviewCount;
    private String coverImageUrl;

    // Business & Local Host details
    private Long ownerId;
    private String ownerName;
    private String ownerHandle;
    private String ownerAvatar;
    private String phoneNumber;
    private String openHours;
    private String priceRange;
    private String categoryName;
    private String amenities;
    private String status;
    private Boolean isVerifiedHost;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

