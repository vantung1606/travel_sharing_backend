package com.wayfare.controller;

import com.wayfare.dto.ApiResponse;
import com.wayfare.dto.AuditLogDto;
import com.wayfare.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class AdminAuditLogController {

    private final ActivityLogService activityLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditLogDto>>> getAuditLogs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("REST request to fetch audit logs: keyword={}, userId={}, action={}, page={}, size={}",
                keyword, userId, action, page, size);
        Page<AuditLogDto> logPage = activityLogService.searchLogs(keyword, userId, action, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách nhật ký hệ thống thành công", logPage));
    }

    @GetMapping("/actions")
    public ResponseEntity<ApiResponse<List<String>>> getAvailableActions() {
        log.info("REST request to fetch distinct activity log actions");
        List<String> actions = activityLogService.getAvailableActions();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách loại hành động thành công", actions));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAuditStats() {
        log.info("REST request to fetch audit log summary stats");
        Map<String, Object> stats = activityLogService.getAuditStats();
        return ResponseEntity.ok(ApiResponse.success("Lấy thống kê nhật ký thành công", stats));
    }
}
