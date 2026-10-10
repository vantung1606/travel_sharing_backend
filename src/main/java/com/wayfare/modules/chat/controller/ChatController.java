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
        User user = userRepository.findByEmail(effectiveEmail)
                .orElseGet(() -> userRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng trong hệ thống")));

        if (user.getLastLoginAt() == null || java.time.Duration.between(user.getLastLoginAt(), java.time.LocalDateTime.now()).toMinutes() >= 2) {
            user.setLastLoginAt(java.time.LocalDateTime.now());
            user = userRepository.save(user);
        }
        return user;
    }

    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<List<ChatRoomDto>>> getUserRooms(
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        User user = resolveUser(email);
        log.info("REST request to get chat rooms for: {}", user.getEmail());
        List<ChatRoomDto> rooms = chatService.getUserRooms(user);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách phòng chat thành công", rooms));
    }

    @GetMapping("/available-users")
    public ResponseEntity<ApiResponse<List<ChatMemberDto>>> getAvailableUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        User user = resolveUser(email);
        log.info("REST request to get available users for group creation by {}: keyword={}", user.getEmail(), keyword);
        List<ChatMemberDto> available = chatService.getAvailableUsersForGroup(user, keyword);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách người dùng khả dụng thành công", available));
    }

    @PostMapping("/rooms")
    public ResponseEntity<ApiResponse<ChatRoomDto>> createGroupRoom(
            @Valid @RequestBody CreateRoomRequest request,
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        User user = resolveUser(email);
        log.info("REST request to create group room by {}: {}", user.getEmail(), request.getName());
        ChatRoomDto room = chatService.createGroupRoom(user, request);
        return ResponseEntity.ok(ApiResponse.success("Tạo nhóm chat thành công", room));
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

    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(
            @PathVariable Long roomId,
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        User user = resolveUser(email);
        log.info("REST request to delete room: {} by: {}", roomId, user.getEmail());
        chatService.deleteRoom(roomId, user);
        return ResponseEntity.ok(ApiResponse.success("Xóa cuộc trò chuyện thành công", null));
    }

    @GetMapping("/rooms/{roomId}/members")
    public ResponseEntity<ApiResponse<List<ChatMemberDto>>> getRoomMembers(
            @PathVariable Long roomId,
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        User user = resolveUser(email);
        log.info("REST request to get members for room: {} by: {}", roomId, user.getEmail());
        List<ChatMemberDto> members = chatService.getRoomMembers(roomId, user);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách thành viên thành công", members));
    }

    @PostMapping("/rooms/{roomId}/members")
    public ResponseEntity<ApiResponse<ChatRoomDto>> addMembersToRoom(
            @PathVariable Long roomId,
            @RequestBody AddMembersRequest request,
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        User user = resolveUser(email);
        log.info("REST request to add members to room: {} by: {}", roomId, user.getEmail());
        ChatRoomDto room = chatService.addMembersToRoom(roomId, user, request);
        return ResponseEntity.ok(ApiResponse.success("Thêm thành viên vào nhóm thành công", room));
    }

    @DeleteMapping("/rooms/{roomId}/members/{targetUserId}")
    public ResponseEntity<ApiResponse<ChatRoomDto>> removeMemberFromRoom(
            @PathVariable Long roomId,
            @PathVariable Long targetUserId,
            @RequestParam(required = false, defaultValue = "tung@gmail.com") String email) {
        User user = resolveUser(email);
        log.info("REST request to remove member: {} from room: {} by: {}", targetUserId, roomId, user.getEmail());
        ChatRoomDto room = chatService.removeMemberFromRoom(roomId, user, targetUserId);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thành viên nhóm thành công", room));
    }
}
