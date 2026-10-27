package com.wayfare.modules.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {
    private String fullName;
    private String handle;
    private String bio;
    private String avatarUrl;
    private String phoneNumber;
    private String address;
    private String travelStyle;
    private String budgetPreference;
}
