package com.wayfare.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserPermissionsRequest {
    @Builder.Default
    private List<String> allow = new ArrayList<>();

    @Builder.Default
    private List<String> deny = new ArrayList<>();
}
