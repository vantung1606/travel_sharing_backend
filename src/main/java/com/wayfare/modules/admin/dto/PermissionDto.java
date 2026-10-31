package com.wayfare.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDto {
    private Long id;
    private String code;
    private String name;
    private String module;
    private String action;
    private String description;
    private Boolean isSystem;
    private Integer sortOrder;
}
