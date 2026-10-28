package com.wayfare.modules.chat.controller;

import com.wayfare.entity.User;
import com.wayfare.modules.chat.dto.ChatMessageDto;
import com.wayfare.modules.chat.dto.SendMessageRequest;
import com.wayfare.modules.chat.service.ChatService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @Data
    public static class WebSocketMessagePayload {
        private Long roomId;
        private String content;
        private String messageType; // TEXT, IMAGE, VIDEO, LOCATION
        private String email;
    }

    @Data
    public static class TypingPayload {
        private Long roomId;
        private Long userId;
        private String email;
        private String userName;
        private Boolean isTyping;
    }

    @Data
    public static class ReadReceiptPayload {
        private Long roomId;
        private Long userId;
        private String email;
        private String userName;
        private Long lastReadMessageId;
    }

    @MessageMapping("/chat.sendMessage")
    public void handleIncomingMessage(@Payload WebSocketMessagePayload payload) {
        if (payload == null || payload.getRoomId() == null || payload.getContent() == null) {
            log.warn("Invalid WebSocket message payload received");
            return;
        }

        if (payload.getEmail() == null || payload.getEmail().isBlank()) {
            log.warn("Rejecting WebSocket message: Sender email is required");
            return;
        }

        String email = payload.getEmail().trim();
        User user;
        try {
            user = chatService.resolveUser(email);
        } catch (Exception e) {
            log.warn("WebSocket message sender not found for email {}: {}", email, e.getMessage());
            return;
        }

        log.info("Processing WebSocket message from {} to room {}: type={}", email, payload.getRoomId(), payload.getMessageType());

        SendMessageRequest request = SendMessageRequest.builder()
                .content(payload.getContent())
                .messageType(payload.getMessageType() != null ? payload.getMessageType() : "TEXT")
                .build();

        // ChatService internally broadcasts to /topic/room.{roomId}
        ChatMessageDto sent = chatService.sendMessage(payload.getRoomId(), user, request);
        log.info("WebSocket message processed and broadcasted with ID: {}", sent.getId());
    }

    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload TypingPayload payload) {
        if (payload == null || payload.getRoomId() == null) {
            return;
        }
        log.debug("Received typing event for room {}: email={}, isTyping={}", payload.getRoomId(), payload.getEmail(), payload.getIsTyping());
        messagingTemplate.convertAndSend("/topic/room." + payload.getRoomId() + "/typing", payload);
    }

    @MessageMapping("/chat.read")
    public void handleReadReceipt(@Payload ReadReceiptPayload payload) {
        if (payload == null || payload.getRoomId() == null || payload.getEmail() == null || payload.getEmail().isBlank()) {
            return;
        }
        try {
            User user = chatService.resolveUser(payload.getEmail().trim());
            chatService.markRoomAsRead(payload.getRoomId(), user);
        } catch (Exception e) {
            log.warn("Failed to process WebSocket read receipt: {}", e.getMessage());
        }
    }
}
