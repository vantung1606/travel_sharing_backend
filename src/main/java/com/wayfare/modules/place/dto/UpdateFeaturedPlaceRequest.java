package com.wayfare.modules.place.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateFeaturedPlaceRequest {
    private Boolean isFeatured;
    private Integer featuredOrder;
    private String featuredBadge;
}
