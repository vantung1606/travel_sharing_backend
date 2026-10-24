package com.wayfare.modules.notification.service;

import com.wayfare.modules.notification.dto.NotificationDto;
import com.wayfare.entity.Notification;
import com.wayfare.entity.User;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.repository.NotificationRepository;
import com.wayfare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getNotificationsForUser(String email) {
        log.info("Fetching notifications for user with email: {}", email);
        if (email == null || email.isBlank()) {
            return java.util.Collections.emptyList();
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return java.util.Collections.emptyList();
        }
        List<Notification> list = notificationRepository.findByRecipientOrderByCreatedAtDesc(user);
        return list.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String email) {
        if (email == null || email.isBlank()) {
            return 0L;
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return 0L;
        }
        long count = notificationRepository.countByRecipientAndIsReadFalse(user);
        log.info("Unread notifications count for user {}: {}", email, count);
        return count;
    }

    @Transactional
    public NotificationDto markAsRead(Long id, String email) {
        log.info("Marking notification id {} as read for user {}", id, email);
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));

        if (email != null && !email.isBlank()) {
            User user = userRepository.findByEmail(email).orElse(null);
            if (user != null && notification.getRecipient() != null && !notification.getRecipient().getId().equals(user.getId())) {
                log.warn("User {} attempted to mark notification of another user", email);
                return mapToDto(notification);
            }
        }

        notification.setIsRead(true);
        Notification saved = notificationRepository.save(notification);
        return mapToDto(saved);
    }

    @Transactional
    public void markAllAsRead(String email) {
        log.info("Marking all notifications as read for user {}", email);
        if (email == null || email.isBlank()) return;
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            notificationRepository.markAllAsReadByRecipient(user);
        }
    }

    @Transactional
    public void deleteNotification(Long id, String email) {
        log.info("Deleting notification id {} for user {}", id, email);
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        if (email != null && !email.isBlank()) {
            User user = userRepository.findByEmail(email).orElse(null);
            if (user != null && notification.getRecipient() != null && !notification.getRecipient().getId().equals(user.getId())) {
                throw new ResourceNotFoundException("Bạn không có quyền xóa thông báo này");
            }
        }
        notificationRepository.delete(notification);
    }

    @Transactional
    public NotificationDto createNotification(NotificationDto request, String creatorEmail) {
        log.info("Creating notification: type={}, targetUrl={}", request.getType(), request.getTargetUrl());

        User recipient = request.getRecipientId() != null
                ? userRepository.findById(request.getRecipientId()).orElse(null)
                : (creatorEmail != null ? userRepository.findByEmail(creatorEmail).orElse(null) : null);

        if (recipient == null) {
            throw new ResourceNotFoundException("Không tìm thấy người nhận thông báo");
        }

        User actor = null;
        if (request.getActorId() != null) {
            actor = userRepository.findById(request.getActorId()).orElse(null);
        } else if (creatorEmail != null && !creatorEmail.isBlank()) {
            actor = userRepository.findByEmail(creatorEmail).orElse(null);
        }

        Notification notification = Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(request.getType() != null ? request.getType() : "SYSTEM")
                .message(request.getMessage() != null ? request.getMessage() : "Bạn có thông báo mới từ hệ thống.")
                .targetUrl(request.getTargetUrl() != null ? request.getTargetUrl() : "#")
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Successfully created notification with id: {}", saved.getId());
        NotificationDto dto = mapToDto(saved);

        // Phát sóng thời gian thực qua STOMP WebSocket
        try {
            if (recipient != null && recipient.getId() != null) {
                messagingTemplate.convertAndSend("/topic/notifications." + recipient.getId(), dto);
            } else if (recipient != null && recipient.getEmail() != null) {
                messagingTemplate.convertAndSend("/topic/notifications." + recipient.getEmail().toLowerCase().trim(), dto);
            }
        } catch (Exception e) {
            log.warn("Could not dispatch real-time WebSocket notification from createNotification: {}", e.getMessage());
        }

        return dto;
    }

    @Transactional
    public NotificationDto sendNotification(User recipient, User actor, String type, String message, String targetUrl) {
        if (recipient == null) {
            log.warn("Cannot send notification: recipient is null");
            return null;
        }
        log.info("Sending notification to recipient {}: type={}, message={}", recipient.getEmail(), type, message);
        Notification notification = Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(type != null ? type : "SYSTEM")
                .message(message)
                .targetUrl(targetUrl != null ? targetUrl : "#")
                .isRead(false)
                .build();
        Notification saved = notificationRepository.save(notification);
        NotificationDto dto = mapToDto(saved);

        // Phát sóng thời gian thực qua STOMP WebSocket đến kênh riêng của người nhận
        try {
            if (recipient != null && recipient.getId() != null) {
                messagingTemplate.convertAndSend("/topic/notifications." + recipient.getId(), dto);
            } else if (recipient != null && recipient.getEmail() != null) {
                messagingTemplate.convertAndSend("/topic/notifications." + recipient.getEmail().toLowerCase().trim(), dto);
            }
            log.info("Dispatched real-time notification to /topic/notifications.{} for recipient: {}", recipient.getId(), recipient.getEmail());
        } catch (Exception e) {
            log.warn("Could not dispatch real-time WebSocket notification: {}", e.getMessage());
        }

        return dto;
    }

    @Transactional
    public int sendBroadcastNotification(String type, String message, String targetUrl, String senderEmail) {
        log.info("Broadcasting notification to all users: sender={}, type={}, message={}", senderEmail, type, message);
        User actor = (senderEmail != null && !senderEmail.isBlank())
                ? userRepository.findByEmail(senderEmail).orElse(null)
                : null;

        List<User> allUsers = userRepository.findAll();
        List<Notification> notifications = allUsers.stream()
                .map(user -> Notification.builder()
                        .recipient(user)
                        .actor(actor)
                        .type(type != null ? type : "SYSTEM")
                        .message(message)
                        .targetUrl(targetUrl != null ? targetUrl : "#")
                        .isRead(false)
                        .build())
                .collect(Collectors.toList());

        notificationRepository.saveAll(notifications);
        log.info("Broadcasted notification to {} users successfully.", notifications.size());

        // Phát sóng thời gian thực đến kênh chung /topic/notifications.broadcast
        try {
            NotificationDto broadcastDto = NotificationDto.builder()
                    .type(type != null ? type : "SYSTEM")
                    .message(message)
                    .targetUrl(targetUrl != null ? targetUrl : "#")
                    .time("Vừa xong")
                    .actorName(actor != null ? (actor.getFullName() != null ? actor.getFullName() : actor.getEmail()) : "Quản trị viên")
                    .actorAvatar(actor != null ? actor.getAvatarUrl() : null)
                    .isRead(false)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();
            messagingTemplate.convertAndSend("/topic/notifications.broadcast", broadcastDto);
            log.info("Dispatched broadcast notification via WebSocket to /topic/notifications.broadcast");
        } catch (Exception e) {
            log.warn("Could not dispatch broadcast WebSocket notification: {}", e.getMessage());
        }

        return notifications.size();
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            cleanupNonAdminReportAlerts();
        } catch (Exception e) {
            log.warn("Spurious notification cleanup on startup skipped: {}", e.getMessage());
        }
    }

    @Transactional
    public void notifyAdmins(String type, String message, String targetUrl) {
        notifyAdmins(type, message, targetUrl, null);
    }

    @Transactional
    public void notifyAdmins(String type, String message, String targetUrl, User excludeUser) {
        log.info("Sending admin-only notification: type={}, message={}, excludeUser={}",
                type, message, excludeUser != null ? excludeUser.getEmail() : "none");
        List<User> admins = userRepository.searchUsers(null, null, "ROLE_ADMIN");
        if (admins.isEmpty()) {
            userRepository.findByEmail("admin@gmail.com").ifPresent(admins::add);
        }

        for (User admin : admins) {
            // Không gửi thông báo cho chính người vừa thực hiện thao tác
            if (excludeUser != null && admin.getId() != null && admin.getId().equals(excludeUser.getId())) {
                continue;
            }
            sendNotification(admin, excludeUser, type != null ? type : "REPORT_ALERT", message, targetUrl != null ? targetUrl : "/admin/reports");
        }
        log.info("Successfully sent admin notification to relevant admins.");
    }

    @Transactional
    public int cleanupNonAdminReportAlerts() {
        try {
            int deleted = notificationRepository.deleteSpuriousAdminNotifications();
            if (deleted > 0) {
                log.info("Cleaned up {} spurious admin notifications from non-admin accounts.", deleted);
            }
            return deleted;
        } catch (Exception e) {
            log.warn("Spurious notification cleanup encountered: {}", e.getMessage());
            return 0;
        }
    }

    public User getUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return userRepository.findByEmail(email).orElse(null);
    }


    private NotificationDto mapToDto(Notification entity) {
        User actor = entity.getActor();
        return NotificationDto.builder()
                .id(entity.getId())
                .recipientId(entity.getRecipient() != null ? entity.getRecipient().getId() : null)
                .actorId(actor != null ? actor.getId() : null)
                .actorName(actor != null ? actor.getFullName() : "Hệ Thống Wayfare")
                .actorHandle(actor != null ? actor.getHandle() : "@wayfare_system")
                .actorAvatar(actor != null && actor.getAvatarUrl() != null
                        ? actor.getAvatarUrl()
                        : "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80")
                .type(entity.getType())
                .message(entity.getMessage())
                .targetUrl(entity.getTargetUrl())
                .isRead(entity.getIsRead())
                .time(entity.getCreatedAt() != null ? entity.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")) : "Vừa xong")
                .createdAt(entity.getCreatedAt())
                .build();
    }
}


