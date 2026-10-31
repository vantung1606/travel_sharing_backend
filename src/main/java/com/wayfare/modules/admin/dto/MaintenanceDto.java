package com.wayfare.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceDto {
    @Builder.Default
    private Boolean enabled = false;
    
    @Builder.Default
    private String title = "Hệ thống đang bảo trì nâng cấp";
    
    @Builder.Default
    private String message = "Wayfare đang thực hiện nâng cấp hệ thống định kỳ nhằm mang lại trải nghiệm khám phá du lịch mượt mà hơn. Rất mong bạn thông cảm và quay lại sau ít phút!";
    
    private String estimatedEndTime;
    
    @Builder.Default
    private Boolean allowAdminBypass = true;
    
    private String updatedByEmail;
    private String updatedAt;
}
