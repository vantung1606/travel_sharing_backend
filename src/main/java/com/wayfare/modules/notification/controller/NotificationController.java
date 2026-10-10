package com.wayfare.modules.notification.controller;

import com.wayfare.modules.admin.service.ActivityLogService;

import com.wayfare.modules.notification.dto.NotificationDto;
import com.wayfare.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getNotifications(
            @RequestParam(value = "email", required = false, defaultValue = "admin@gmail.com") String email) {
        log.info("REST request to get notifications for email: {}", email);
        List<NotificationDto> result = notificationService.getNotificationsForUser(email);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestParam(value = "email", required = false, defaultValue = "admin@gmail.com") String email) {
        log.info("REST request to get unread notifications count for email: {}", email);
        long count = notificationService.getUnreadCount(email);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationDto> markAsRead(
            @PathVariable Long id,
            @RequestParam(value = "email", required = false, defaultValue = "admin@gmail.com") String email) {
        log.info("REST request to mark notification {} as read", id);
        NotificationDto updated = notificationService.markAsRead(id, email);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/read-all")
    public ResponseEntity<Map<String, String>> markAllAsRead(
            @RequestParam(value = "email", required = false, defaultValue = "admin@gmail.com") String email) {
        log.info("REST request to mark all notifications as read for: {}", email);
        notificationService.markAllAsRead(email);
        return ResponseEntity.ok(Map.of("message", "Đã đánh dấu tất cả thông báo là đã đọc"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteNotification(
            @PathVariable Long id,
            @RequestParam(value = "email", required = false, defaultValue = "admin@gmail.com") String email) {
        log.info("REST request to delete notification {}", id);
        notificationService.deleteNotification(id, email);
        return ResponseEntity.ok(Map.of("message", "Đã xóa thông báo thành công"));
    }

    @PostMapping("/test")
    public ResponseEntity<NotificationDto> createTestNotification(
            @RequestBody NotificationDto request,
            @RequestParam(value = "email", required = false, defaultValue = "admin@gmail.com") String email) {
        log.info("REST request to create test notification: {}", request.getMessage());
        NotificationDto created = notificationService.createNotification(request, email);
        return ResponseEntity.ok(created);
    }

    @PostMapping("/broadcast")
    public ResponseEntity<Map<String, Object>> broadcastNotification(
            @RequestBody NotificationDto request,
            @RequestParam(value = "senderEmail", required = false, defaultValue = "admin@gmail.com") String senderEmail,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        log.info("REST request to broadcast notification to all users: type={}, message={}", request.getType(), request.getMessage());
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


