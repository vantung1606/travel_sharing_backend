package com.wayfare.service;

import com.wayfare.dto.NotificationDto;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<NotificationDto> getNotificationsForUser(String email) {
        log.info("Fetching notifications for user with email: {}", email);
        User user = getUserByEmailOrDefault(email);
        List<Notification> list = notificationRepository.findByRecipientOrderByCreatedAtDesc(user);
        return list.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String email) {
        User user = getUserByEmailOrDefault(email);
        long count = notificationRepository.countByRecipientAndIsReadFalse(user);
        log.info("Unread notifications count for user {}: {}", email, count);
        return count;
    }

    @Transactional
    public NotificationDto markAsRead(Long id, String email) {
        log.info("Marking notification id {} as read for user {}", id, email);
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));

        notification.setIsRead(true);
        Notification saved = notificationRepository.save(notification);
        return mapToDto(saved);
    }

    @Transactional
    public void markAllAsRead(String email) {
        log.info("Marking all notifications as read for user {}", email);
        User user = getUserByEmailOrDefault(email);
        notificationRepository.markAllAsReadByRecipient(user);
    }

    @Transactional
    public void deleteNotification(Long id, String email) {
        log.info("Deleting notification id {} for user {}", id, email);
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        notificationRepository.delete(notification);
    }

    @Transactional
    public NotificationDto createNotification(NotificationDto request, String creatorEmail) {
        log.info("Creating notification: type={}, targetUrl={}", request.getType(), request.getTargetUrl());

        User recipient = request.getRecipientId() != null
                ? userRepository.findById(request.getRecipientId()).orElse(getUserByEmailOrDefault(creatorEmail))
                : getUserByEmailOrDefault(creatorEmail);

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
        return mapToDto(saved);
    }

    private User getUserByEmailOrDefault(String email) {
        if (email != null && !email.isBlank()) {
            return userRepository.findByEmail(email)
                    .orElseGet(this::getDefaultUser);
        }
        return getDefaultUser();
    }

    private User getDefaultUser() {
        return userRepository.findByEmail("admin@gmail.com")
                .or(() -> userRepository.findAll().stream().findFirst())
                .orElseThrow(() -> new ResourceNotFoundException("No valid recipient user found"));
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
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
