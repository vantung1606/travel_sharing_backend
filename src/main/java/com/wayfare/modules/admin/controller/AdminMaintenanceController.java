package com.wayfare.modules.admin.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.modules.admin.dto.MaintenanceDto;
import com.wayfare.modules.admin.service.ActivityLogService;
import com.wayfare.modules.admin.service.MaintenanceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/maintenance")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class AdminMaintenanceController {

    private final MaintenanceService maintenanceService;
    private final ActivityLogService activityLogService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<MaintenanceDto>> getMaintenance() {
        log.info("REST request by admin to get maintenance mode status");
        MaintenanceDto status = maintenanceService.getMaintenanceStatus();
        return ResponseEntity.ok(ApiResponse.success("Lấy trạng thái bảo trì thành công", status));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping
    public ResponseEntity<ApiResponse<MaintenanceDto>> updateMaintenance(
            @RequestBody MaintenanceDto request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        String adminEmail = authentication != null ? authentication.getName() : "admin";
        String ip = activityLogService.extractClientIp(httpRequest);
        String ua = httpRequest != null && httpRequest.getHeader("User-Agent") != null ? httpRequest.getHeader("User-Agent") : "Unknown";

        log.info("REST request by admin {} to update maintenance status: enabled={}", adminEmail, request.getEnabled());
        MaintenanceDto updated = maintenanceService.updateMaintenanceStatus(request, adminEmail, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật chế độ bảo trì hệ thống thành công!", updated));
    }
}
