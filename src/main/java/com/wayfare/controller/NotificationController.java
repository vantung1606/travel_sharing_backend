package com.wayfare.controller;

import com.wayfare.dto.NotificationDto;
import com.wayfare.service.NotificationService;
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
}
