package com.wayfare.modules.chat.service;

import com.wayfare.entity.*;
import com.wayfare.exception.ResourceNotFoundException;
import com.wayfare.modules.chat.dto.*;
import com.wayfare.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final UserRepository userRepository;
    private final ItineraryRepository itineraryRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Transactional(readOnly = true)
    public List<ChatRoomDto> getUserRooms(User currentUser) {
        log.info("Fetching chat rooms for user: {} (id={})", currentUser.getEmail(), currentUser.getId());
        List<ChatRoom> rooms = chatRoomRepository.findRoomsByUserId(currentUser.getId());

        // Nếu người dùng chưa có phòng chat nào, tự động khởi tạo các phòng mẫu để trải nghiệm ngay
        if (rooms.isEmpty()) {
            initSampleRoomsForUser(currentUser);
            rooms = chatRoomRepository.findRoomsByUserId(currentUser.getId());
        }

        return rooms.stream()
                .map(room -> mapToRoomDto(room, currentUser))
                .collect(Collectors.toList());
    }

    @Transactional
    public List<ChatMessageDto> getRoomMessages(Long roomId, User currentUser) {
        log.info("Fetching messages for room: {} by user: {}", roomId, currentUser.getEmail());
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng chat không tồn tại: " + roomId));

        // Tự động gia nhập phòng nếu là người dùng hợp lệ
        ChatMember member = chatMemberRepository.findByChatRoomIdAndUserId(roomId, currentUser.getId())
                .orElseGet(() -> {
                    ChatMember newMember = ChatMember.builder()
                            .chatRoom(room)
                            .user(currentUser)
                            .role("MEMBER")
                            .build();
                    return chatMemberRepository.save(newMember);
                });

        List<ChatMessage> messages = chatMessageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId);

        // Cập nhật mốc tin nhắn đã đọc gần nhất
        if (!messages.isEmpty()) {
            ChatMessage latest = messages.get(messages.size() - 1);
            member.setLastReadMessageId(latest.getId());
            chatMemberRepository.save(member);
        }

        return messages.stream()
                .map(m -> mapToMessageDto(m, currentUser))
                .collect(Collectors.toList());
    }

    @Transactional
    public ChatMessageDto sendMessage(Long roomId, User currentUser, SendMessageRequest request) {
        log.info("User {} sending message to room {}: type={}", currentUser.getEmail(), roomId, request.getMessageType());

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng chat không tồn tại: " + roomId));

        // Đảm bảo là thành viên phòng
        ChatMember member = chatMemberRepository.findByChatRoomIdAndUserId(roomId, currentUser.getId())
                .orElseGet(() -> {
                    ChatMember newMember = ChatMember.builder()
                            .chatRoom(room)
                            .user(currentUser)
                            .role("MEMBER")
                            .build();
                    return chatMemberRepository.save(newMember);
                });

        String msgType = request.getMessageType() != null && !request.getMessageType().isBlank()
                ? request.getMessageType().toUpperCase()
                : "TEXT";

        ChatMessage message = ChatMessage.builder()
                .chatRoom(room)
                .sender(currentUser)
                .content(request.getContent().trim())
                .messageType(msgType)
                .build();

        ChatMessage saved = chatMessageRepository.save(message);

        // Cập nhật thời gian phòng và mốc đọc của người gửi
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomRepository.save(room);

        member.setLastReadMessageId(saved.getId());
        chatMemberRepository.save(member);

        ChatMessageDto dto = mapToMessageDto(saved, currentUser);

        // Phát sóng thời gian thực qua WebSocket STOMP tới tất cả thành viên trong phòng
        try {
            messagingTemplate.convertAndSend("/topic/room." + roomId, dto);
            log.info("Broadcasted chat message {} to /topic/room.{}", saved.getId(), roomId);
        } catch (Exception e) {
            log.warn("Could not broadcast message via WebSocket broker: {}", e.getMessage());
        }

        return dto;
    }

    @Transactional
    public ChatRoomDto getOrCreateDirectRoom(User currentUser, Long targetUserId) {
        log.info("Get or create direct room between user {} and user {}", currentUser.getId(), targetUserId);
        if (currentUser.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("Không thể tạo cuộc trò chuyện trực tiếp với chính mình.");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại: " + targetUserId));

        List<ChatRoom> existing = chatRoomRepository.findDirectRoomBetweenUsers(currentUser.getId(), targetUserId);
        if (!existing.isEmpty()) {
            return mapToRoomDto(existing.get(0), currentUser);
        }

        // Tạo phòng chat 1-1 mới
        ChatRoom newRoom = ChatRoom.builder()
                .name(targetUser.getFullName())
                .type("DIRECT")
                .creator(currentUser)
                .build();
        ChatRoom savedRoom = chatRoomRepository.save(newRoom);

        ChatMember m1 = ChatMember.builder().chatRoom(savedRoom).user(currentUser).role("OWNER").build();
        ChatMember m2 = ChatMember.builder().chatRoom(savedRoom).user(targetUser).role("MEMBER").build();
        chatMemberRepository.saveAll(List.of(m1, m2));

        // Tin nhắn chào mừng khởi tạo
        ChatMessage welcome = ChatMessage.builder()
                .chatRoom(savedRoom)
                .sender(currentUser)
                .content("Xin chào! Rất vui được kết nối cùng bạn trên Wayfare.")
                .messageType("TEXT")
                .build();
        chatMessageRepository.save(welcome);

        return mapToRoomDto(savedRoom, currentUser);
    }

    @Transactional
    public ChatRoomDto getOrCreateItineraryRoom(Long itineraryId, User currentUser) {
        log.info("Get or create chat room for itinerary: {} by user: {}", itineraryId, currentUser.getEmail());
        Itinerary itinerary = itineraryRepository.findById(itineraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Lịch trình không tồn tại: " + itineraryId));

        Optional<ChatRoom> existing = chatRoomRepository.findByItineraryId(itineraryId);
        if (existing.isPresent()) {
            ChatRoom room = existing.get();
            if (!chatMemberRepository.existsByChatRoomIdAndUserId(room.getId(), currentUser.getId())) {
                chatMemberRepository.save(ChatMember.builder().chatRoom(room).user(currentUser).role("MEMBER").build());
            }
            return mapToRoomDto(room, currentUser);
        }

        // Tạo phòng chat nhóm gắn với chuyến đi
        ChatRoom groupRoom = ChatRoom.builder()
                .name("Nhóm chuyến đi: " + itinerary.getTitle())
                .type("GROUP")
                .itinerary(itinerary)
                .creator(itinerary.getCreator() != null ? itinerary.getCreator() : currentUser)
                .build();
        ChatRoom savedRoom = chatRoomRepository.save(groupRoom);

        chatMemberRepository.save(ChatMember.builder()
                .chatRoom(savedRoom)
                .user(currentUser)
                .role("MEMBER")
                .build());

        if (itinerary.getCreator() != null && !itinerary.getCreator().getId().equals(currentUser.getId())) {
            chatMemberRepository.save(ChatMember.builder()
                    .chatRoom(savedRoom)
                    .user(itinerary.getCreator())
                    .role("OWNER")
                    .build());
        }

        ChatMessage welcome = ChatMessage.builder()
                .chatRoom(savedRoom)
                .sender(currentUser)
                .content("Chào mừng các bạn đến với nhóm thảo luận chuyến đi " + itinerary.getTitle() + "! Hãy cùng chia sẻ lịch trình, kinh nghiệm và ảnh nhé.")
                .messageType("TEXT")
                .build();
        chatMessageRepository.save(welcome);

        return mapToRoomDto(savedRoom, currentUser);
    }

    private ChatRoomDto mapToRoomDto(ChatRoom room, User currentUser) {
        List<ChatMember> members = chatMemberRepository.findByChatRoomId(room.getId());
        Optional<ChatMessage> latestMsg = chatMessageRepository.findTop1ByChatRoomIdOrderByCreatedAtDesc(room.getId());

        // Tìm thành viên hiện tại để tính unread
        Optional<ChatMember> myMember = members.stream()
                .filter(m -> m.getUser().getId().equals(currentUser.getId()))
                .findFirst();

        long unread = 0;
        if (myMember.isPresent()) {
            unread = chatMessageRepository.countUnreadMessages(room.getId(), myMember.get().getLastReadMessageId());
        }

        String displayName = room.getName();
        String displayAvatar = "https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=150&q=80";

        if ("DIRECT".equalsIgnoreCase(room.getType())) {
            // Hiển thị tên và avatar của người đối diện
            Optional<User> other = members.stream()
                    .map(ChatMember::getUser)
                    .filter(u -> !u.getId().equals(currentUser.getId()))
                    .findFirst();

            if (other.isPresent()) {
                displayName = other.get().getFullName() != null ? other.get().getFullName() : other.get().getEmail();
                if (other.get().getAvatarUrl() != null && !other.get().getAvatarUrl().isBlank()) {
                    displayAvatar = other.get().getAvatarUrl();
                } else {
                    displayAvatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80";
                }
            }
        } else if (room.getItinerary() != null && room.getItinerary().getCoverImageUrl() != null) {
            displayAvatar = room.getItinerary().getCoverImageUrl();
        }

        String lastMsgText = "Chưa có tin nhắn";
        String timeStr = formatTime(room.getUpdatedAt() != null ? room.getUpdatedAt() : room.getCreatedAt());

        if (latestMsg.isPresent()) {
            ChatMessage msg = latestMsg.get();
            if ("IMAGE".equalsIgnoreCase(msg.getMessageType())) {
                lastMsgText = "[Hình ảnh 📷]";
            } else if ("VIDEO".equalsIgnoreCase(msg.getMessageType())) {
                lastMsgText = "[Video 🎬]";
            } else if ("LOCATION".equalsIgnoreCase(msg.getMessageType())) {
                lastMsgText = "[Vị trí 📍]";
            } else {
                lastMsgText = msg.getContent();
            }
            timeStr = formatTime(msg.getCreatedAt());
        }

        List<ChatMemberDto> memberDtos = members.stream().map(m -> ChatMemberDto.builder()
                .userId(m.getUser().getId())
                .fullName(m.getUser().getFullName())
                .handle(m.getUser().getHandle())
                .email(m.getUser().getEmail())
                .avatarUrl(m.getUser().getAvatarUrl())
                .role(m.getRole())
                .build()
        ).collect(Collectors.toList());

        return ChatRoomDto.builder()
                .id(room.getId())
                .name(displayName)
                .type(room.getType())
                .itineraryId(room.getItinerary() != null ? room.getItinerary().getId() : null)
                .itineraryTitle(room.getItinerary() != null ? room.getItinerary().getTitle() : null)
                .membersCount(members.size())
                .avatar(displayAvatar)
                .lastMsg(lastMsgText)
                .time(timeStr)
                .unread((int) unread)
                .updatedAt(room.getUpdatedAt() != null ? room.getUpdatedAt() : room.getCreatedAt())
                .members(memberDtos)
                .build();
    }

    private ChatMessageDto mapToMessageDto(ChatMessage m, User currentUser) {
        User sender = m.getSender();
        boolean isMe = sender != null && sender.getId().equals(currentUser.getId());
        String avatar = sender != null && sender.getAvatarUrl() != null && !sender.getAvatarUrl().isBlank()
                ? sender.getAvatarUrl()
                : "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80";

        return ChatMessageDto.builder()
                .id(m.getId())
                .roomId(m.getChatRoom().getId())
                .senderId(sender != null ? sender.getId() : null)
                .senderName(isMe ? "Bạn" : (sender != null ? sender.getFullName() : "Thành viên"))
                .senderHandle(sender != null ? sender.getHandle() : null)
                .senderAvatar(avatar)
                .content(m.getContent())
                .messageType(m.getMessageType())
                .time(formatTime(m.getCreatedAt()))
                .createdAt(m.getCreatedAt())
                .isMe(isMe)
                .build();
    }

    private String formatTime(LocalDateTime dt) {
        if (dt == null) return "";
        LocalDateTime now = LocalDateTime.now();
        if (dt.toLocalDate().isEqual(now.toLocalDate())) {
            return dt.format(TIME_FMT);
        } else if (dt.toLocalDate().isEqual(now.toLocalDate().minusDays(1))) {
            return "Hôm qua";
        } else {
            return dt.format(DATE_FMT);
        }
    }

    private void initSampleRoomsForUser(User user) {
        try {
            log.info("Initializing sample chat rooms for user: {}", user.getEmail());
            // 1. Nhóm Phượt Hà Giang 3N2Đ
            ChatRoom group1 = ChatRoom.builder()
                    .name("Nhóm Phượt Hà Giang 3N2Đ (Hội phượt thủ)")
                    .type("GROUP")
                    .creator(user)
                    .build();
            ChatRoom savedG1 = chatRoomRepository.save(group1);
            chatMemberRepository.save(ChatMember.builder().chatRoom(savedG1).user(user).role("OWNER").build());

            // Tìm thêm 1 user khác nếu có để add vào nhóm
            List<User> otherUsers = userRepository.findAll().stream()
                    .filter(u -> !u.getId().equals(user.getId()))
                    .limit(3)
                    .collect(Collectors.toList());

            for (User u : otherUsers) {
                chatMemberRepository.save(ChatMember.builder().chatRoom(savedG1).user(u).role("MEMBER").build());
            }

            User firstOther = !otherUsers.isEmpty() ? otherUsers.get(0) : user;

            chatMessageRepository.save(ChatMessage.builder()
                    .chatRoom(savedG1)
                    .sender(firstOther)
                    .content("Mọi người nhớ chuẩn bị áo khoác dày nhé, đêm trên Đồng Văn khá lạnh!")
                    .messageType("TEXT")
                    .build());

            chatMessageRepository.save(ChatMessage.builder()
                    .chatRoom(savedG1)
                    .sender(user)
                    .content("Đã chuẩn bị đầy đủ rồi nha. Đặt vé thuyền sông Nho Quế thành công luôn rồi!")
                    .messageType("TEXT")
                    .build());

            // 2. Hội Đi Đà Nẵng
            ChatRoom group2 = ChatRoom.builder()
                    .name("Hội Đi Đà Nẵng - Hội An")
                    .type("GROUP")
                    .creator(user)
                    .build();
            ChatRoom savedG2 = chatRoomRepository.save(group2);
            chatMemberRepository.save(ChatMember.builder().chatRoom(savedG2).user(user).role("OWNER").build());
            chatMessageRepository.save(ChatMessage.builder()
                    .chatRoom(savedG2)
                    .sender(firstOther)
                    .content("Chào cả nhóm! Khách sạn gần biển Mỹ Khê đã được chốt xong rồi nhé.")
                    .messageType("TEXT")
                    .build());

            // 3. Chat 1-1 nếu có người dùng khác
            if (!otherUsers.isEmpty()) {
                ChatRoom direct = ChatRoom.builder()
                        .name(firstOther.getFullName())
                        .type("DIRECT")
                        .creator(user)
                        .build();
                ChatRoom savedD = chatRoomRepository.save(direct);
                chatMemberRepository.save(ChatMember.builder().chatRoom(savedD).user(user).role("OWNER").build());
                chatMemberRepository.save(ChatMember.builder().chatRoom(savedD).user(firstOther).role("MEMBER").build());
                chatMessageRepository.save(ChatMessage.builder()
                        .chatRoom(savedD)
                        .sender(firstOther)
                        .content("Chào bạn! Chuyến đi Đà Nẵng lần trước mình có đăng bài chia sẻ chi tiết trên trang cộng đồng đó, bạn xem thử nhé.")
                        .messageType("TEXT")
                        .build());
            }
        } catch (Exception e) {
            log.error("Failed to initialize sample rooms: {}", e.getMessage(), e);
        }
    }
}
