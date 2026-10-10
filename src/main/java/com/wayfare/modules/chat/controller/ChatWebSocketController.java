package com.wayfare.modules.chat.controller;

import com.wayfare.entity.User;
import com.wayfare.modules.chat.dto.ChatMessageDto;
import com.wayfare.modules.chat.dto.SendMessageRequest;
import com.wayfare.modules.chat.service.ChatService;
import com.wayfare.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final ChatService chatService;

    @Data
    public static class WebSocketMessagePayload {
        private Long roomId;
        private String content;
        private String messageType; // TEXT, IMAGE, VIDEO, LOCATION
        private String email;
    }

    @MessageMapping("/chat.sendMessage")
    public void handleIncomingMessage(@Payload WebSocketMessagePayload payload) {
        if (payload == null || payload.getRoomId() == null || payload.getContent() == null) {
            log.warn("Invalid WebSocket message payload received");
            return;
        }

        String email = payload.getEmail() != null && !payload.getEmail().isBlank()
                ? payload.getEmail().trim()
                : "tung@gmail.com";

        User user = null;
        try {
            user = chatService.resolveUser(email);
        } catch (Exception e) {
            log.warn("WebSocket message sender not found for email: {}", email);
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
}
