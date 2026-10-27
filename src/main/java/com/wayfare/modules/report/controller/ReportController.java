package com.wayfare.modules.report.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.modules.report.dto.CreateReportRequest;
import com.wayfare.modules.report.dto.ReportDto;
import com.wayfare.modules.report.service.SystemReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private final SystemReportService systemReportService;

    private String requireAuthEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new ResourceNotFoundException("Vui lòng đăng nhập để gửi báo cáo vi phạm");
        }
        return authentication.getName();
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReportDto>> submitReport(
            @Valid @RequestBody CreateReportRequest request,
            Authentication authentication) {
        String reporterEmail = requireAuthEmail(authentication);
        log.info("REST request to submit report: targetType={}, targetId={}, by={}",
                request.getTargetType(), request.getTargetId(), reporterEmail);
        ReportDto report = systemReportService.submitReport(request, reporterEmail);
        return ResponseEntity.ok(ApiResponse.success("Báo cáo vi phạm đã được gửi thành công! Quản trị viên sẽ sớm xem xét xử lý.", report));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    @GetMapping("/admin/system")
    public ResponseEntity<ApiResponse<List<ReportDto>>> getAdminSystemReports(
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false, defaultValue = "ALL") String targetType) {
        log.info("REST request to fetch system reports for Admin: status={}, targetType={}", status, targetType);
        List<ReportDto> reports = systemReportService.getReports(status, targetType);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách báo cáo hệ thống thành công", reports));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    @PutMapping("/admin/system/{id}/resolve")
    public ResponseEntity<ApiResponse<ReportDto>> resolveSystemReport(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        String adminEmail = requireAuthEmail(authentication);
        String action = body.getOrDefault("action", "RESOLVED");
        String notes = body.getOrDefault("notes", "");
        log.info("REST request by admin {} to resolve system report ID {}: action={}", adminEmail, id, action);
        ReportDto resolved = systemReportService.resolveReport(id, action, notes, adminEmail);
        return ResponseEntity.ok(ApiResponse.success("Xử lý báo cáo vi phạm thành công!", resolved));
    }
}
