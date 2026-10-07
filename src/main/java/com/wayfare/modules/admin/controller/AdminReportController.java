package com.wayfare.modules.admin.controller;

import com.wayfare.modules.admin.dto.AdminPostDto;
import com.wayfare.modules.admin.dto.AdminReportMetricsDto;
import com.wayfare.modules.admin.service.AdminReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminReportController {

    private final AdminReportService adminReportService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getReportsRoot(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "all") String status
    ) {
        log.info("REST request to root /api/admin/reports, keyword: '{}', status: '{}'", keyword, status);
        List<AdminPostDto> posts = adminReportService.getAllArticles(keyword, status);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", posts);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        log.info("REST request to get admin report moderation metrics");
        AdminReportMetricsDto metrics = adminReportService.getMetrics();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", metrics);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pending")
    public ResponseEntity<Map<String, Object>> getPendingReports(
            @RequestParam(required = false) String keyword
    ) {
        log.info("REST request to get pending report escalation cards, keyword: '{}'", keyword);
        List<AdminPostDto> reports = adminReportService.getReportedPosts(keyword);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", reports);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllArticles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "all") String status
    ) {
        log.info("REST request to get all community articles for moderation, keyword: '{}', status: '{}'", keyword, status);
        List<AdminPostDto> posts = adminReportService.getAllArticles(keyword, status);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", posts);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{postId}/approve")
    public ResponseEntity<Map<String, Object>> approvePost(@PathVariable Long postId) {
        log.info("REST request to approve post ID: {}", postId);
        AdminPostDto updated = adminReportService.approvePost(postId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Đã phê duyệt xuất bản bài viết #" + postId + " lên Cộng đồng thành công!");
        response.put("data", updated);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{postId}/dismiss")
    public ResponseEntity<Map<String, Object>> dismissReport(@PathVariable Long postId) {
        log.info("REST request to dismiss report for post ID: {}", postId);
        AdminPostDto updated = adminReportService.dismissReport(postId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Đã bác bỏ báo cáo bài viết #" + postId + " (Nội dung hợp lệ)");
        response.put("data", updated);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{postId}/hide")
    public ResponseEntity<Map<String, Object>> hidePost(@PathVariable Long postId) {
        log.info("REST request to hide post ID: {}", postId);
        AdminPostDto updated = adminReportService.hidePost(postId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Đã tạm ẩn bài viết #" + postId + " khỏi cộng đồng");
        response.put("data", updated);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{postId}/remove-and-ban")
    public ResponseEntity<Map<String, Object>> removePostAndBanAuthor(@PathVariable Long postId) {
        log.info("REST request to remove post ID: {} and ban author", postId);
        AdminPostDto updated = adminReportService.removePostAndBanAuthor(postId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Đã gỡ bài #" + postId + " và khóa tài khoản vi phạm");
        response.put("data", updated);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Map<String, Object>> deletePost(@PathVariable Long postId) {
        log.info("REST request to permanently delete post ID: {}", postId);
        adminReportService.deletePost(postId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Đã xóa vĩnh viễn bài viết #" + postId);
        return ResponseEntity.ok(response);
    }
}


