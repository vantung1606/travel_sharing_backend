package com.wayfare.controller;

import com.wayfare.dto.ApiResponse;
import com.wayfare.dto.AuditLogDto;
import com.wayfare.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
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
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("REST request to fetch audit logs: keyword={}, userId={}, action={}, category={}, page={}, size={}",
                keyword, userId, action, category, page, size);
        Page<AuditLogDto> logPage = activityLogService.searchLogs(keyword, userId, action, category, page, size);
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

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadAuditLogFile(
            @RequestParam(required = false, defaultValue = "ALL") String category
    ) {
        log.info("REST request to download audit log file for category: {}", category);
        try {
            byte[] fileBytes = activityLogService.getLogFileBytes(category);
            String filename = "wayfare-" + category.toLowerCase() + "-" + System.currentTimeMillis() + ".log";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(fileBytes);
        } catch (IOException e) {
            log.error("Failed to download log file for category {}: {}", category, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
