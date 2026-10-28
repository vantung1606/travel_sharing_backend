package com.wayfare.modules.chat.controller;

import com.wayfare.common.dto.ApiResponse;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.entity.User;
import com.wayfare.modules.chat.dto.*;
import com.wayfare.modules.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class ChatController {

    private final ChatService chatService;

    private User resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new ResourceNotFoundException("Vui lòng đăng nhập để sử dụng tính năng trò chuyện");
        }
        return chatService.resolveUser(authentication.getName());
    }

    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<List<ChatRoomDto>>> getUserRooms(Authentication authentication) {
        User user = resolveUser(authentication);
        log.info("REST request to get chat rooms for: {}", user.getEmail());
        List<ChatRoomDto> rooms = chatService.getUserRooms(user);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách phòng chat thành công", rooms));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<java.util.Map<String, Long>>> getTotalUnreadCount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            return ResponseEntity.ok(ApiResponse.success("Chưa đăng nhập", java.util.Map.of("unreadCount", 0L)));
        }
        User user = chatService.resolveUser(authentication.getName());
        long totalUnread = chatService.getTotalUnreadCount(user);
        return ResponseEntity.ok(ApiResponse.success("Lấy tổng số tin nhắn chưa đọc thành công", java.util.Map.of("unreadCount", totalUnread)));
    }

    @GetMapping("/available-users")
    public ResponseEntity<ApiResponse<List<ChatMemberDto>>> getAvailableUsers(
            @RequestParam(required = false) String keyword,
            Authentication authentication) {
        User user = resolveUser(authentication);
        log.info("REST request to get available users for group creation by {}: keyword={}", user.getEmail(), keyword);
        List<ChatMemberDto> available = chatService.getAvailableUsersForGroup(user, keyword);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách người dùng khả dụng thành công", available));
    }

    @PostMapping("/rooms")
    public ResponseEntity<ApiResponse<ChatRoomDto>> createGroupRoom(
            @Valid @RequestBody CreateRoomRequest request,
            Authentication authentication) {
        User user = resolveUser(authentication);
        log.info("REST request to create group room by {}: {}", user.getEmail(), request.getName());
        ChatRoomDto room = chatService.createGroupRoom(user, request);
        return ResponseEntity.ok(ApiResponse.success("Tạo nhóm chat thành công", room));
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageDto>>> getRoomMessages(
            @PathVariable Long roomId,
            Authentication authentication) {
        User user = resolveUser(authentication);
        log.info("REST request to get messages for room: {} by user: {}", roomId, user.getEmail());
        List<ChatMessageDto> messages = chatService.getRoomMessages(roomId, user);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử tin nhắn thành công", messages));
    }

    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> markRoomAsRead(
            @PathVariable Long roomId,
            Authentication authentication) {
        User user = resolveUser(authentication);
        log.info("REST request to mark room {} as read by: {}", roomId, user.getEmail());
        Long latestId = chatService.markRoomAsRead(roomId, user);
        return ResponseEntity.ok(ApiResponse.success("Đánh dấu đã đọc thành công", java.util.Map.of(
                "roomId", roomId,
                "lastReadMessageId", latestId
        )));
    }

    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<ChatMessageDto>> sendMessage(
            @PathVariable Long roomId,
            @Valid @RequestBody SendMessageRequest request,
            Authentication authentication) {
        User user = resolveUser(authentication);
        log.info("REST request to send message to room: {} by user: {} (type={})", roomId, user.getEmail(), request.getMessageType());
        ChatMessageDto sent = chatService.sendMessage(roomId, user, request);
        return ResponseEntity.ok(ApiResponse.success("Gửi tin nhắn thành công", sent));
    }

    @PostMapping("/direct")
    public ResponseEntity<ApiResponse<ChatRoomDto>> getOrCreateDirectRoom(
            @RequestParam Long targetUserId,
            Authentication authentication) {
        User user = resolveUser(authentication);
        log.info("REST request to get/create direct room with user: {} by: {}", targetUserId, user.getEmail());
        ChatRoomDto room = chatService.getOrCreateDirectRoom(user, targetUserId);
        return ResponseEntity.ok(ApiResponse.success("Mở cuộc trò chuyện thành công", room));
    }

    @PostMapping("/itinerary/{itineraryId}")
    public ResponseEntity<ApiResponse<ChatRoomDto>> getOrCreateItineraryRoom(
            @PathVariable Long itineraryId,
            Authentication authentication) {
        User user = resolveUser(authentication);
        log.info("REST request to get/create chat room for itinerary: {} by: {}", itineraryId, user.getEmail());
        ChatRoomDto room = chatService.getOrCreateItineraryRoom(itineraryId, user);
        return ResponseEntity.ok(ApiResponse.success("Tham gia nhóm chuyến đi thành công", room));
    }

    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(
            @PathVariable Long roomId,
            Authentication authentication) {
        User user = resolveUser(authentication);
        log.info("REST request to delete room: {} by: {}", roomId, user.getEmail());
        chatService.deleteRoom(roomId, user);
        return ResponseEntity.ok(ApiResponse.success("Xóa cuộc trò chuyện thành công", null));
    }

    @GetMapping("/rooms/{roomId}/members")
    public ResponseEntity<ApiResponse<List<ChatMemberDto>>> getRoomMembers(
            @PathVariable Long roomId,
            Authentication authentication) {
        User user = resolveUser(authentication);
        log.info("REST request to get members for room: {} by: {}", roomId, user.getEmail());
        List<ChatMemberDto> members = chatService.getRoomMembers(roomId, user);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách thành viên thành công", members));
    }

    @PostMapping("/rooms/{roomId}/members")
    public ResponseEntity<ApiResponse<ChatRoomDto>> addMembersToRoom(
            @PathVariable Long roomId,
            @RequestBody AddMembersRequest request,
            Authentication authentication) {
        User user = resolveUser(authentication);
        log.info("REST request to add members to room: {} by: {}", roomId, user.getEmail());
        ChatRoomDto room = chatService.addMembersToRoom(roomId, user, request);
        return ResponseEntity.ok(ApiResponse.success("Thêm thành viên vào nhóm thành công", room));
    }

    @DeleteMapping("/rooms/{roomId}/members/{targetUserId}")
    public ResponseEntity<ApiResponse<ChatRoomDto>> removeMemberFromRoom(
            @PathVariable Long roomId,
            @PathVariable Long targetUserId,
            Authentication authentication) {
        User user = resolveUser(authentication);
        log.info("REST request to remove member: {} from room: {} by: {}", targetUserId, roomId, user.getEmail());
        ChatRoomDto room = chatService.removeMemberFromRoom(roomId, user, targetUserId);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thành viên nhóm thành công", room));
    }
}
