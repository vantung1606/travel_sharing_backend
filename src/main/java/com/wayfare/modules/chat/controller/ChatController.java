package com.wayfare.modules.chat.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.entity.User;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.modules.chat.dto.*;
import com.wayfare.modules.chat.service.ChatService;
import com.wayfare.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    private User resolveUser(String email) {
        String effectiveEmail = (email != null && !email.isBlank()) ? email.trim() : "tung@gmail.com";
        return userRepository.findByEmail(effectiveEmail)
                .orElseGet(() -> userRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng trong hệ thống")));
    }

    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<List<ChatRoomDto>>> getUserRooms(
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        User user = resolveUser(email);
        log.info("REST request to get chat rooms for: {}", user.getEmail());
        List<ChatRoomDto> rooms = chatService.getUserRooms(user);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách phòng chat thành công", rooms));
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageDto>>> getRoomMessages(
            @PathVariable Long roomId,
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        User user = resolveUser(email);
        log.info("REST request to get messages for room: {} by user: {}", roomId, user.getEmail());
        List<ChatMessageDto> messages = chatService.getRoomMessages(roomId, user);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử tin nhắn thành công", messages));
    }

    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<ChatMessageDto>> sendMessage(
            @PathVariable Long roomId,
            @Valid @RequestBody SendMessageRequest request,
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        User user = resolveUser(email);
        log.info("REST request to send message to room: {} by user: {} (type={})", roomId, user.getEmail(), request.getMessageType());
        ChatMessageDto sent = chatService.sendMessage(roomId, user, request);
        return ResponseEntity.ok(ApiResponse.success("Gửi tin nhắn thành công", sent));
    }

    @PostMapping("/direct")
    public ResponseEntity<ApiResponse<ChatRoomDto>> getOrCreateDirectRoom(
            @RequestParam Long targetUserId,
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        User user = resolveUser(email);
        log.info("REST request to get/create direct room with user: {} by: {}", targetUserId, user.getEmail());
        ChatRoomDto room = chatService.getOrCreateDirectRoom(user, targetUserId);
        return ResponseEntity.ok(ApiResponse.success("Mở cuộc trò chuyện thành công", room));
    }

    @PostMapping("/itinerary/{itineraryId}")
    public ResponseEntity<ApiResponse<ChatRoomDto>> getOrCreateItineraryRoom(
            @PathVariable Long itineraryId,
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        User user = resolveUser(email);
        log.info("REST request to get/create chat room for itinerary: {} by: {}", itineraryId, user.getEmail());
        ChatRoomDto room = chatService.getOrCreateItineraryRoom(itineraryId, user);
        return ResponseEntity.ok(ApiResponse.success("Tham gia nhóm chuyến đi thành công", room));
    }
}
