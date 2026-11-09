package com.wayfare.modules.admin.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.modules.admin.dto.SystemMonitoringDto;
import com.wayfare.modules.admin.dto.SystemMonitoringDto.DatabaseTableDetailDto;
import com.wayfare.modules.admin.service.SystemMonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/system")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class AdminSystemMonitoringController {

    private final SystemMonitoringService systemMonitoringService;

    /**
     * GET /api/admin/system/overview
     * Trả về toàn bộ thông số phiên bản, môi trường runtime JVM, OS, bộ nhớ và danh sách các bảng CSDL.
     * Chỉ tài khoản có ROLE_ADMIN mới có quyền xem thông tin hệ thống sâu này.
     */
    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SystemMonitoringDto>> getSystemOverview() {
        log.info("REST request by admin to get comprehensive system monitoring data");
        SystemMonitoringDto data = systemMonitoringService.getSystemMonitoringData();
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin giám sát hệ thống thành công", data));
    }

    /**
     * GET /api/admin/system/tables/{tableName}
     * Trả về cấu trúc các cột (schema) và thông số kỹ thuật của bảng CSDL cụ thể.
     */
    @GetMapping("/tables/{tableName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DatabaseTableDetailDto>> getTableDetails(@PathVariable String tableName) {
        log.info("REST request by admin to get schema details for database table: {}", tableName);
        DatabaseTableDetailDto detail = systemMonitoringService.getTableDetails(tableName);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết bảng " + tableName + " thành công", detail));
    }

    /**
     * POST /api/admin/system/gc
     * Yêu cầu JVM thực thi Garbage Collector giải phóng bộ nhớ heap không sử dụng.
     */
    @PostMapping("/gc")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> triggerGarbageCollection() {
        log.info("REST request by admin to manually trigger JVM Garbage Collection");
        System.gc();
        return ResponseEntity.ok(ApiResponse.success("Đã gửi tín hiệu thu gom bộ nhớ (GC) tới JVM", "OK"));
    }
}
