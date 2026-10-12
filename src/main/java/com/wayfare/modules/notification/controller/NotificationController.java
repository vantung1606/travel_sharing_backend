package com.wayfare.modules.notification.controller;

import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.modules.admin.service.ActivityLogService;
import com.wayfare.modules.notification.dto.NotificationDto;
import com.wayfare.modules.notification.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;

    private String extractEmail(Authentication authentication, String queryEmail) {
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        return queryEmail;
    }

    private String requireAuthEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new ResourceNotFoundException("Vui lòng đăng nhập để xem thông báo");
        }
        return authentication.getName();
    }

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getNotifications(
            @RequestParam(value = "email", required = false) String email,
            Authentication authentication) {
        String targetEmail = extractEmail(authentication, email);
        log.info("REST request to get notifications for email: {}", targetEmail);
        List<NotificationDto> result = notificationService.getNotificationsForUser(targetEmail);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestParam(value = "email", required = false) String email,
            Authentication authentication) {
        String targetEmail = extractEmail(authentication, email);
        log.info("REST request to get unread notifications count for email: {}", targetEmail);
        long count = notificationService.getUnreadCount(targetEmail);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationDto> markAsRead(
            @PathVariable Long id,
            @RequestParam(value = "email", required = false) String email,
            Authentication authentication) {
        String targetEmail = extractEmail(authentication, email);
        log.info("REST request to mark notification {} as read by: {}", id, targetEmail);
        NotificationDto updated = notificationService.markAsRead(id, targetEmail);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/read-all")
    public ResponseEntity<Map<String, String>> markAllAsRead(
            @RequestParam(value = "email", required = false) String email,
            Authentication authentication) {
        String targetEmail = extractEmail(authentication, email);
        log.info("REST request to mark all notifications as read for: {}", targetEmail);
        notificationService.markAllAsRead(targetEmail);
        return ResponseEntity.ok(Map.of("message", "Đã đánh dấu tất cả thông báo là đã đọc"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteNotification(
            @PathVariable Long id,
            @RequestParam(value = "email", required = false) String email,
            Authentication authentication) {
        String targetEmail = extractEmail(authentication, email);
        log.info("REST request to delete notification {} by: {}", id, targetEmail);
        notificationService.deleteNotification(id, targetEmail);
        return ResponseEntity.ok(Map.of("message", "Đã xóa thông báo thành công"));
    }

    @PostMapping("/test")
    public ResponseEntity<NotificationDto> createTestNotification(
            @RequestBody NotificationDto request,
            @RequestParam(value = "email", required = false) String email,
            Authentication authentication) {
        String senderEmail = extractEmail(authentication, email);
        log.info("REST request to create test notification: {}", request.getMessage());
        NotificationDto created = notificationService.createNotification(request, senderEmail);
        return ResponseEntity.ok(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/broadcast")
    public ResponseEntity<Map<String, Object>> broadcastNotification(
            @RequestBody NotificationDto request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        String senderEmail = requireAuthEmail(authentication);
        log.info("REST request to broadcast notification to all users by {}: type={}, message={}", senderEmail, request.getType(), request.getMessage());
        int count = notificationService.sendBroadcastNotification(
                request.getType(),
                request.getMessage(),
                request.getTargetUrl(),
                senderEmail
        );

        try {
            com.wayfare.entity.User sender = notificationService.getUserByEmail(senderEmail);
            String ip = activityLogService.extractClientIp(httpRequest);
            String ua = httpRequest != null && httpRequest.getHeader("User-Agent") != null ? httpRequest.getHeader("User-Agent") : "Mozilla/5.0";
            activityLogService.recordLog(sender, "BROADCAST_NOTIFICATION", "Phát thông báo toàn hệ thống đến " + count + " người dùng: " + request.getMessage(), ip, ua);
        } catch (Exception e) {
            log.warn("Failed to record activity log for broadcast: {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã phát thông báo thành công đến " + count + " người dùng",
                "recipientCount", count
        ));
    }
}
