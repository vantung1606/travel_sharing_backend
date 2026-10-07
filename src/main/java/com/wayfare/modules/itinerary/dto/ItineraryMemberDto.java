package com.wayfare.modules.itinerary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItineraryMemberDto {
    private Long id;
    private Long itineraryId;
    private Long userId;
    private String fullName;
    private String email;
    private String avatarUrl;
    private String role; // OWNER, EDITOR, VIEWER
    private LocalDateTime joinedAt;
}

