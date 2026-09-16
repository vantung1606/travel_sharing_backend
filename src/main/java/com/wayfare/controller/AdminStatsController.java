package com.wayfare.controller;

import com.wayfare.dto.AdminStatsDto;
import com.wayfare.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    /**
     * GET /api/admin/stats
     * Returns aggregated system statistics for the Admin dashboard.
     * Requires ADMIN role.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminStatsDto> getSystemStats() {
        log.info("Admin requested system statistics");
        AdminStatsDto stats = adminStatsService.getSystemStats();
        return ResponseEntity.ok(stats);
    }
}
