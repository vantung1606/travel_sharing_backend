package com.wayfare.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePlaceRequest {
    @NotBlank(message = "Tên địa điểm hoặc tên quán không được để trống")
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
}
