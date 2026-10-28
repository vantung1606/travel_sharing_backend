package com.wayfare.modules.place.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePlaceRequest {
    private String name;
    private String description;
    private String address;
    private String city;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal ticketPrice;
    private String priceRange;
    private String openHours;
    private String phoneNumber;
    private String categoryName;
    private String amenities;
    private String coverImageUrl;
    private List<String> images;
    private String status;
    private Boolean isVerifiedHost;
}
