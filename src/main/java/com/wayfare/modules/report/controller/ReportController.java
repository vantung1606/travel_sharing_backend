package com.wayfare.modules.report.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.modules.report.dto.CreateReportRequest;
import com.wayfare.modules.report.dto.ReportDto;
import com.wayfare.modules.report.service.ReportService;
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

    private final ReportService reportService;

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
        ReportDto report = reportService.submitReport(request, reporterEmail);
        return ResponseEntity.ok(ApiResponse.success("Báo cáo vi phạm đã được gửi thành công! Quản trị viên sẽ sớm xem xét xử lý.", report));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    @GetMapping({"/admin", "/admin/system"})
    public ResponseEntity<ApiResponse<List<ReportDto>>> getAdminReports(
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false, defaultValue = "ALL") String targetType) {
        log.info("REST request to fetch reports for Admin: status={}, targetType={}", status, targetType);
        List<ReportDto> reports = reportService.getReports(status, targetType);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách báo cáo vi phạm thành công", reports));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    @PutMapping({"/admin/{id}/resolve", "/admin/system/{id}/resolve"})
    public ResponseEntity<ApiResponse<ReportDto>> resolveReport(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        String adminEmail = requireAuthEmail(authentication);
        String action = body != null ? body.get("action") : "RESOLVED";
        String notes = body != null ? body.get("notes") : "";

        log.info("REST request to resolve report ID {} by {}: action={}, notes={}", id, adminEmail, action, notes);
        ReportDto updated = reportService.resolveReport(id, action, notes, adminEmail);
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật trạng thái xử lý báo cáo thành công!", updated));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReport(
            @PathVariable Long id,
            Authentication authentication) {
        String adminEmail = requireAuthEmail(authentication);
        log.info("REST request to delete report ID {} by {}", id, adminEmail);
        reportService.deleteReport(id, adminEmail);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa báo cáo thành công!", null));
    }
}
