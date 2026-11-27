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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayfare.modules.ai.dto.AiGenerateRequest;
import com.wayfare.modules.ai.dto.AiGenerateResponse;
import com.wayfare.modules.ai.service.AiAssistantContextService;
import com.wayfare.modules.ai.service.AiService;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    private final com.wayfare.modules.notification.service.NotificationService notificationService;
    private final AiService aiService;
    private final AiAssistantContextService aiAssistantContextService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static final String AI_ASSISTANT_EMAIL = "assistant@wayfare.com";
    public static final String AI_ASSISTANT_NAME = "Trợ lý Wayfare";
    public static final String AI_ASSISTANT_HANDLE = "wayfare_assistant";
    public static final String AI_ASSISTANT_AVATAR = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=300&q=80";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Transactional
    public User resolveUser(String email) {
        if (email == null || email.isBlank()) {
            throw new ResourceNotFoundException("Vui lòng đăng nhập để sử dụng tính năng trò chuyện");
        }
        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));

        if (user.getLastLoginAt() == null || Duration.between(user.getLastLoginAt(), LocalDateTime.now()).toMinutes() >= 2) {
            user.setLastLoginAt(LocalDateTime.now());
            user = userRepository.save(user);
            log.info("Refreshed lastLoginAt for active chatter: {}", user.getEmail());
        }
        return user;
    }

    @Transactional
    public User getOrCreateAiAssistantUser() {
        return userRepository.findByEmail(AI_ASSISTANT_EMAIL)
                .orElseGet(() -> {
                    User bot = User.builder()
                            .email(AI_ASSISTANT_EMAIL)
                            .password("NO_PASSWORD_BOT_ACCOUNT")
                            .fullName(AI_ASSISTANT_NAME)
                            .handle(AI_ASSISTANT_HANDLE)
                            .avatarUrl(AI_ASSISTANT_AVATAR)
                            .bio("Trợ lý du lịch ảo WanderAI luôn sẵn sàng hỗ trợ bạn 24/7.")
                            .isVerified(true)
                            .status("ACTIVE")
                            .isLocked(false)
                            .tier("diamond")
                            .travelStyle("Thông thái & Nhiệt tình")
                            .budgetPreference("Linh hoạt")
                            .build();
                    log.info("Initialized system AI Assistant user: {}", AI_ASSISTANT_EMAIL);
                    return userRepository.save(bot);
                });
    }

    @Transactional
    public ChatRoom getOrCreateAiAssistantRoom(User currentUser) {
        User assistant = getOrCreateAiAssistantUser();
        List<ChatRoom> existing = chatRoomRepository.findDirectRoomBetweenUsers(currentUser.getId(), assistant.getId());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        ChatRoom aiRoom = ChatRoom.builder()
                .name(AI_ASSISTANT_NAME)
                .type("DIRECT")
                .creator(assistant)
                .build();
        ChatRoom savedRoom = chatRoomRepository.save(aiRoom);

        ChatMember m1 = ChatMember.builder().chatRoom(savedRoom).user(currentUser).role("MEMBER").build();
        ChatMember m2 = ChatMember.builder().chatRoom(savedRoom).user(assistant).role("OWNER").build();
        chatMemberRepository.saveAll(List.of(m1, m2));

        ChatMessage welcome = ChatMessage.builder()
                .chatRoom(savedRoom)
                .sender(assistant)
                .content("Xin chào " + (currentUser.getFullName() != null ? currentUser.getFullName() : "bạn") + "! 🌟\n\nMình là Trợ lý Wayfare. Bạn có thể hỏi mình bất cứ điều gì về gợi ý điểm đến, lịch trình du lịch, món ngon đặc sản hay thời tiết nhé!")
                .messageType("TEXT")
                .build();
        chatMessageRepository.save(welcome);
        log.info("Initialized pinned AI Assistant room #{} for user {}", savedRoom.getId(), currentUser.getEmail());
        return savedRoom;
    }

    @Transactional
    public List<ChatRoomDto> getUserRooms(User currentUser) {
        log.info("Fetching chat rooms for user: {} (id={})", currentUser.getEmail(), currentUser.getId());

        // Đảm bảo mọi tài khoản (mới hoặc cũ) đều có phòng Trợ lý Wayfare
        ChatRoom aiRoom = getOrCreateAiAssistantRoom(currentUser);

        List<ChatRoom> rooms = chatRoomRepository.findRoomsByUserId(currentUser.getId());

        // Lọc trùng lặp tuyệt đối đối với phòng DIRECT:
        // Đảm bảo cùng một người chỉ xuất hiện DUY NHẤT 1 LẦN trong danh sách trò chuyện
        Map<Long, ChatRoom> directRoomsByOtherUserId = new LinkedHashMap<>();
        List<ChatRoom> resultRooms = new ArrayList<>();

        for (ChatRoom room : rooms) {
            if ("DIRECT".equalsIgnoreCase(room.getType())) {
                List<ChatMember> members = chatMemberRepository.findByChatRoomId(room.getId());
                // Tự động làm sạch nếu phòng 1-1 bị dính thêm thành viên thứ 3
                if (members.size() > 2) {
                    log.warn("Corrupted DIRECT room #{} has {} members. Sanitizing...", room.getId(), members.size());
                    Long creatorId = room.getCreator() != null ? room.getCreator().getId() : members.get(0).getUser().getId();
                    ChatMember keep1 = null;
                    for (ChatMember m : members) {
                        if (m.getUser().getId().equals(creatorId)) {
                            keep1 = m;
                            break;
                        }
                    }
                    if (keep1 == null) {
                        keep1 = members.get(0);
                    }

                    ChatMember keep2 = null;
                    for (ChatMember m : members) {
                        if (!m.getUser().getId().equals(keep1.getUser().getId()) && m.getUser().getId().equals(currentUser.getId())) {
                            keep2 = m;
                            break;
                        }
                    }
                    if (keep2 == null) {
                        for (ChatMember m : members) {
                            if (!m.getUser().getId().equals(keep1.getUser().getId())) {
                                keep2 = m;
                                break;
                            }
                        }
                    }
                    for (ChatMember m : members) {
                        if (!m.getId().equals(keep1.getId()) && (keep2 == null || !m.getId().equals(keep2.getId()))) {
                            log.info("Evicting unauthorized member {} from DIRECT room #{}", m.getUser().getEmail(), room.getId());
                            chatMemberRepository.delete(m);
                        }
                    }
                    members = chatMemberRepository.findByChatRoomId(room.getId());
                }

                Optional<User> other = members.stream()
                        .map(ChatMember::getUser)
                        .filter(u -> !u.getId().equals(currentUser.getId()))
                        .findFirst();

                if (other.isPresent()) {
                    Long otherId = other.get().getId();
                    if (!directRoomsByOtherUserId.containsKey(otherId)) {
                        directRoomsByOtherUserId.put(otherId, room);
                        resultRooms.add(room);
                    }
                } else {
                    resultRooms.add(room);
                }
            } else {
                resultRooms.add(room);
            }
        }

        // Đảm bảo aiRoom luôn có mặt trong resultRooms
        if (resultRooms.stream().noneMatch(r -> r.getId().equals(aiRoom.getId()))) {
            resultRooms.add(0, aiRoom);
        }

        // Sắp xếp: Phòng Trợ lý Wayfare (isPinned/AI) LUÔN ĐỨNG ĐẦU TIÊN (Index 0), sau đó theo updatedAt desc
        return resultRooms.stream()
                .map(room -> mapToRoomDto(room, currentUser))
                .sorted((r1, r2) -> {
                    boolean p1 = Boolean.TRUE.equals(r1.getIsPinned()) || "AI".equalsIgnoreCase(r1.getType());
                    boolean p2 = Boolean.TRUE.equals(r2.getIsPinned()) || "AI".equalsIgnoreCase(r2.getType());
                    if (p1 && !p2) return -1;
                    if (!p1 && p2) return 1;
                    LocalDateTime t1 = r1.getUpdatedAt() != null ? r1.getUpdatedAt() : LocalDateTime.MIN;
                    LocalDateTime t2 = r2.getUpdatedAt() != null ? r2.getUpdatedAt() : LocalDateTime.MIN;
                    return t2.compareTo(t1);
                })
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
                    if ("DIRECT".equalsIgnoreCase(room.getType())) {
                        if (room.getCreator() != null && room.getCreator().getId().equals(currentUser.getId())) {
                            log.info("Restoring missing OWNER member for creator {} in DIRECT room {}", currentUser.getEmail(), roomId);
                            ChatMember restored = ChatMember.builder()
                                    .chatRoom(room)
                                    .user(currentUser)
                                    .role("OWNER")
                                    .build();
                            return chatMemberRepository.save(restored);
                        }
                        log.warn("Blocked unauthorized attempt to enter DIRECT room {} by user {}", roomId, currentUser.getEmail());
                        throw new ResourceNotFoundException("Bạn không phải thành viên của cuộc trò chuyện 1-1 này.");
                    }
                    ChatMember newMember = ChatMember.builder()
                            .chatRoom(room)
                            .user(currentUser)
                            .role("MEMBER")
                            .build();
                    return chatMemberRepository.save(newMember);
                });

        List<ChatMessage> messages = chatMessageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId);

        // Cập nhật mốc tin nhắn đã đọc gần nhất cho currentUser
        if (!messages.isEmpty()) {
            ChatMessage latest = messages.get(messages.size() - 1);
            member.setLastReadMessageId(latest.getId());
            chatMemberRepository.save(member);
        }

        // Lấy mốc đọc lớn nhất của các thành viên khác trong phòng
        List<ChatMember> otherMembers = chatMemberRepository.findByChatRoomId(roomId).stream()
                .filter(m -> !m.getUser().getId().equals(currentUser.getId()))
                .collect(Collectors.toList());
        Long maxOtherLastReadId = otherMembers.stream()
                .map(ChatMember::getLastReadMessageId)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L);

        return messages.stream()
                .map(m -> {
                    ChatMessageDto dto = mapToMessageDto(m, currentUser);
                    if (Boolean.TRUE.equals(dto.getIsMe())) {
                        dto.setIsRead(m.getId() <= maxOtherLastReadId);
                    } else {
                        dto.setIsRead(true);
                    }
                    return dto;
                })
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
                    if ("DIRECT".equalsIgnoreCase(room.getType())) {
                        if (room.getCreator() != null && room.getCreator().getId().equals(currentUser.getId())) {
                            log.info("Restoring missing OWNER member for creator {} in DIRECT room {}", currentUser.getEmail(), roomId);
                            ChatMember restored = ChatMember.builder()
                                    .chatRoom(room)
                                    .user(currentUser)
                                    .role("OWNER")
                                    .build();
                            return chatMemberRepository.save(restored);
                        }
                        log.warn("Blocked unauthorized attempt to send to DIRECT room {} by user {}", roomId, currentUser.getEmail());
                        throw new ResourceNotFoundException("Bạn không thể gửi tin nhắn vào cuộc trò chuyện 1-1 của người khác.");
                    }
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
        dto.setIsRead(false);

        // Phát sóng thời gian thực qua WebSocket STOMP tới phòng đang mở
        try {
            ChatMessageDto broadcastDto = mapToMessageDto(saved, null);
            broadcastDto.setIsRead(false);
            messagingTemplate.convertAndSend("/topic/room." + roomId, broadcastDto);
            log.info("Broadcasted chat message {} to /topic/room.{}", saved.getId(), roomId);
        } catch (Exception e) {
            log.warn("Could not broadcast message via WebSocket broker: {}", e.getMessage());
        }

        // Phát sóng cập nhật danh sách Hộp thư và tin nhắn mới (realtime bên ngoài) tới từng thành viên
        try {
            List<ChatMember> allRoomMembers = chatMemberRepository.findByChatRoomId(roomId);
            for (ChatMember cm : allRoomMembers) {
                if (cm.getUser() != null) {
                    ChatRoomDto memberRoomDto = mapToRoomDto(room, cm.getUser());
                    String userTopic = "/topic/user." + cm.getUser().getId() + ".chat";
                    messagingTemplate.convertAndSend(userTopic, memberRoomDto);
                    if (cm.getUser().getEmail() != null) {
                        String emailTopic = "/topic/user." + cm.getUser().getEmail().toLowerCase().trim() + ".chat";
                        messagingTemplate.convertAndSend(emailTopic, memberRoomDto);
                    }
                }
            }
            log.info("Broadcasted room update for room {} to all members", roomId);
        } catch (Exception e) {
            log.warn("Could not broadcast room updates to members: {}", e.getMessage());
        }

        // Gửi thông báo hệ thống real-time đến các thành viên khác trong phòng
        try {
            List<ChatMember> roomMembers = chatMemberRepository.findByChatRoomId(roomId);
            String preview = "IMAGE".equalsIgnoreCase(request.getMessageType()) ? "[Hình ảnh 📷]" :
                    "VIDEO".equalsIgnoreCase(request.getMessageType()) ? "[Video 🎬]" :
                    (request.getContent() != null && request.getContent().length() > 60
                            ? request.getContent().substring(0, 57) + "..."
                            : request.getContent());

            String senderDisplayName = currentUser.getFullName() != null && !currentUser.getFullName().isBlank()
                    ? currentUser.getFullName()
                    : currentUser.getEmail();

            String notifMessage = "GROUP".equalsIgnoreCase(room.getType())
                    ? senderDisplayName + " trong nhóm \"" + (room.getName() != null ? room.getName() : "Trò chuyện") + "\": " + preview
                    : senderDisplayName + ": " + preview;

            for (ChatMember cm : roomMembers) {
                if (cm.getUser() != null && !cm.getUser().getId().equals(currentUser.getId())) {
                    notificationService.sendNotification(
                            cm.getUser(),
                            currentUser,
                            "CHAT_MESSAGE",
                            notifMessage,
                            "/messages?roomId=" + roomId
                    );
                }
            }
        } catch (Exception e) {
            log.warn("Failed to dispatch chat notifications to room members: {}", e.getMessage());
        }

        // Tự động kích hoạt phản hồi thông minh nếu gửi vào phòng Trợ lý Wayfare
        boolean isAiAssistantRoom = "AI".equalsIgnoreCase(room.getType()) ||
                chatMemberRepository.findByChatRoomId(roomId).stream()
                        .anyMatch(cm -> cm.getUser() != null && AI_ASSISTANT_EMAIL.equalsIgnoreCase(cm.getUser().getEmail()));

        if (isAiAssistantRoom && "TEXT".equalsIgnoreCase(msgType)) {
            triggerAiAssistantReply(room, currentUser, request.getContent().trim());
        }

        return dto;
    }

    private void sendAiTypingEvent(Long roomId, User assistant, boolean isTyping) {
        try {
            messagingTemplate.convertAndSend("/topic/room." + roomId + ".typing", Map.of(
                    "email", AI_ASSISTANT_EMAIL,
                    "userName", assistant != null && assistant.getFullName() != null ? assistant.getFullName() : AI_ASSISTANT_NAME,
                    "isTyping", isTyping
            ));
        } catch (Exception ignored) {
        }
    }

    private void triggerAiAssistantReply(ChatRoom room, User user, String userQuestion) {
        final Long roomId = room.getId();
        final Long userId = user.getId();
        CompletableFuture.runAsync(() -> {
            User assistant = null;
            try {
                assistant = getOrCreateAiAssistantUser();
                ChatRoom freshRoom = chatRoomRepository.findById(roomId).orElse(null);
                User freshUser = userRepository.findById(userId).orElse(null);
                if (freshRoom == null || freshUser == null) {
                    log.warn("Cannot generate AI reply: room {} or user {} not found", roomId, userId);
                    return;
                }

                sendAiTypingEvent(roomId, assistant, true);

                String rawResponseText;
                String finalMessageType = "TEXT";

                if (aiService != null && aiService.isApiKeyConfigured()) {
                    // Lấy 12 tin nhắn gần nhất (JOIN FETCH sender để tránh LazyInitializationException trong async thread)
                    List<ChatMessage> recentDesc = chatMessageRepository.findRecentWithSender(
                            roomId, PageRequest.of(0, 12));
                    List<ChatMessage> recentAsc = new ArrayList<>(recentDesc);
                    Collections.reverse(recentAsc);

                    // Bỏ tin nhắn cuối nếu trùng với userQuestion vừa lưu để tránh lặp trong contents
                    if (!recentAsc.isEmpty()) {
                        ChatMessage last = recentAsc.get(recentAsc.size() - 1);
                        if (last.getSender() != null && last.getSender().getId().equals(freshUser.getId())
                                && userQuestion.equals(last.getContent() != null ? last.getContent().trim() : "")) {
                            recentAsc.remove(recentAsc.size() - 1);
                        }
                    }

                    List<Map<String, String>> historyTurns = new ArrayList<>();
                    for (ChatMessage m : recentAsc) {
                        if (m.getContent() == null || m.getContent().isBlank()) continue;
                        boolean isBot = m.getSender() != null && AI_ASSISTANT_EMAIL.equalsIgnoreCase(m.getSender().getEmail());
                        String msgText = m.getContent().trim();
                        if (isBot && "AI_RICH".equalsIgnoreCase(m.getMessageType())) {
                            try {
                                JsonNode node = objectMapper.readTree(msgText);
                                if (node.hasNonNull("reply")) {
                                    msgText = node.get("reply").asText("");
                                }
                            } catch (Exception ignored) {
                            }
                        } else if (!"TEXT".equalsIgnoreCase(m.getMessageType()) && !"AI_RICH".equalsIgnoreCase(m.getMessageType())) {
                            msgText = "[" + m.getMessageType() + "]";
                        }
                        if (!msgText.isBlank()) {
                            historyTurns.add(Map.of(
                                    "role", isBot ? "model" : "user",
                                    "text", msgText
                            ));
                        }
                    }

                    String dbContext = aiAssistantContextService.buildDatabaseContext(freshUser, userQuestion, recentAsc);
                    String systemPrompt = """
                            Bạn là Trợ lý Wayfare VIP — trợ lý du lịch thông minh, đi thẳng vào trọng tâm và có khả năng đọc dữ liệu thật từ CSDL Wayfare cũng như tạo các nút hành động (thêm/sửa/xóa/lưu/mở trang) cho người dùng.

                            QUY TẮC BẮT BUỘC:
                            1. LUÔN đọc kỹ toàn bộ lịch sử hội thoại trước đó. Tuyệt đối KHÔNG hỏi lại những thông tin người dùng đã cung cấp ở các lượt trước (ví dụ đã nói "Hội An" rồi thì lượt sau nói "đi 1 mình" phải hiểu ngay là đi Hội An 1 mình).
                            2. KHÔNG BAO GIỜ nói dối "Mình đã tạo lịch trình..." khi người dùng chưa bấm nút lưu. Hãy nói rõ: "Dưới đây là đề xuất lịch trình ... Bạn có thể xem chi tiết các chặng bên dưới và bấm nút để lưu vào tài khoản nhé!"
                            3. Nếu người dùng chỉ định rõ một huyện/khu vực cụ thể (ví dụ "Nam Đàn, Nghệ An", "Phố cổ Hội An"), ưu tiên gợi ý đúng địa danh và đặc sản của khu vực đó; chỉ gắn `placeId` nếu địa điểm đó thực sự khớp trong danh sách CSDL bên dưới, còn nếu địa điểm nổi tiếng tại địa phương chưa có trong CSDL thì đặt `placeId: null` và vẫn đưa đầy đủ `locationName`, `locationAddress`, `category`, `startTime`, `estimatedCost`, `note` để lịch trình phong phú, thực tế (ít nhất 3-4 hoạt động mỗi ngày: sáng, trưa, chiều, tối).
                            4. TUYỆT ĐỐI KHÔNG dùng ký tự markdown thô như **in đậm**, ## tiêu đề, hay ``` trong văn bản trả lời. VÀ TUYỆT ĐỐI KHÔNG ghi mã kỹ thuật như (placeId: 53) hay (itineraryId: 5) vào nội dung chữ "reply" (chỉ đặt các ID đó vào bên trong "payload" của "actions").
                            5. Phân biệt rõ `actions` và `quickReplies` — KHÔNG ĐƯỢC TRÙNG NHAU:
                               - `actions`: Chứa các nút THAO TÁC DỮ LIỆU (CREATE_ITINERARY kèm đầy đủ details các ngày, OPEN_PLACE, SAVE_PLACE, OPEN_ITINERARY, UPDATE_ITINERARY, DELETE_ITINERARY, ADD_ITINERARY_STOP, UPDATE_ITINERARY_STOP, DELETE_ITINERARY_STOP). Khi tạo CREATE_ITINERARY, luôn thiết kế lịch trình chi tiết, có đủ bữa ăn, tham quan, nghỉ ngơi, không chỉ 1-2 điểm sơ sài.
                               - `quickReplies`: Chứa 3 câu lệnh/câu hỏi TIẾP THEO để tinh chỉnh hoặc mở rộng cuộc trò chuyện (ví dụ: "Đổi sang lịch trình 2 ngày 1 đêm", "Thêm quán ăn đặc sản địa phương", "Tối ưu chi phí tiết kiệm hơn"). Tuyệt đối KHÔNG lặp lại nhãn nút "Xem <tên địa điểm>" vào `quickReplies`.

                            BẮT BUỘC TRẢ VỀ ĐÚNG ĐỊNH DẠNG JSON SAU (không thêm text ngoài JSON):
                            {
                              "reply": "Câu trả lời tiếng Việt sạch sẽ, không chứa ** markdown",
                              "quickReplies": ["Gợi ý ngắn 1", "Gợi ý ngắn 2", "Gợi ý ngắn 3"],
                              "actions": [
                                {
                                  "type": "CREATE_ITINERARY | UPDATE_ITINERARY | DELETE_ITINERARY | CLONE_ITINERARY | TOGGLE_ITINERARY_VISIBILITY | ADD_ITINERARY_STOP | UPDATE_ITINERARY_STOP | DELETE_ITINERARY_STOP | ADD_EXPENSE | SAVE_PLACE | UNSAVE_PLACE | OPEN_PLACE | OPEN_ITINERARY | OPEN_POST | CREATE_POST | NAVIGATE",
                                  "label": "Nhãn nút ngắn gọn có emoji phù hợp",
                                  "payload": {}
                                }
                              ]
                            }

                            CHI TIẾT PAYLOAD CHO TỪNG ACTION TYPE (PHẢI KHỚP 100% SCHEMA HỆ THỐNG WAYFARE):
                            - CREATE_ITINERARY: {"title": "...", "destination": "...", "durationDays": 2, "budgetTotal": 3000000, "groupType": "Cá nhân | Cặp đôi | Bạn bè | Gia đình", "travelStyle": "Nghỉ dưỡng | Phượt / Khám phá | Ẩm thực | Văn hóa & Lịch sử | Sinh thái | Check-in sống ảo", "status": "ACTIVE", "isPublic": true, "details": [{"dayNumber": 1, "dayTitle": "Ngày 1: Khám phá di tích và ẩm thực", "visitOrder": 1, "placeId": 12 (hoặc null nếu ngoài DB), "locationName": "...", "locationAddress": "...", "category": "Tham quan | Ẩm thực | Nghỉ dưỡng | Check-in | Di chuyển", "startTime": "08:30", "estimatedCost": 150000, "transitInfo": "Xe máy / Taxi", "aiTip": "Mẹo ngắn 1 câu", "note": "Ghi chú ngắn"}]}
                              (Lưu ý BẮT BUỘC: MỌI NGÀY từ Ngày 1 đến Ngày cuối cùng đều PHẢI có ĐỦ 3 chặng Sáng - Trưa - Chiều/Tối, tuyệt đối không để ngày nào chỉ có 1-2 chặng sơ sài; tổng chi phí các chặng nên tương xứng với ngân sách. Nếu chuyến đi dài >= 5 ngày thì viết aiTip/note thật ngắn gọn 1 câu. CHỈ TRẢ VỀ DUY NHẤT 1 KHỐI JSON, KHÔNG VIẾT BẤT KỲ CHỮ NÀO BÊN NGOÀI DẤU { }).
                            - UPDATE_ITINERARY: {"itineraryId": 5, "title": "...", "destination": "...", "budgetTotal": 5000000, "groupType": "Cá nhân | Cặp đôi | Bạn bè | Gia đình", "travelStyle": "...", "status": "ACTIVE | DRAFT | COMPLETED | CANCELLED", "isPublic": true} (chỉ truyền field cần sửa + itineraryId bắt buộc)
                            - DELETE_ITINERARY: {"itineraryId": 5, "title": "..."}
                            - ADD_ITINERARY_STOP: {"itineraryId": 5, "itineraryTitle": "Tên lịch trình đích trong CSDL", "dayNumber": 1, "placeId": 12, "locationName": "...", "locationAddress": "...", "category": "Tham quan | Ẩm thực | Nghỉ dưỡng | Check-in", "startTime": "12:00", "estimatedCost": 150000, "transitInfo": "...", "aiTip": "...", "note": "..."}
                              (Bắt buộc: Khi tạo ADD_ITINERARY_STOP, trong "reply" phải nêu rõ tên điểm ăn uống/tham quan cụ thể, địa chỉ/món nổi bật, giá ước tính và nói rõ sẽ thêm vào lịch trình nào, Ngày mấy, Giờ nào!)
                            - UPDATE_ITINERARY_STOP: {"detailId": 21, "itineraryTitle": "...", "locationName": "...", "dayNumber": 1, "startTime": "10:00", "estimatedCost": 120000, "note": "..."}
                            - DELETE_ITINERARY_STOP: {"detailId": 21, "itineraryTitle": "...", "dayNumber": 1, "locationName": "..."}
                            - CLONE_ITINERARY: {"itineraryId": 5, "title": "..."}
                            - TOGGLE_ITINERARY_VISIBILITY: {"itineraryId": 5, "isPublic": true, "title": "..."}
                            - ADD_EXPENSE: {"itineraryId": 5, "itineraryTitle": "...", "amount": 350000, "category": "Ăn uống | Di chuyển | Lưu trú | Tham quan | Mua sắm | Khác", "description": "..."}
                            - CREATE_POST: {"title": "...", "content": "...", "locationTag": "...", "itineraryId": 5}
                            - SAVE_PLACE / UNSAVE_PLACE / OPEN_PLACE: {"placeId": 12, "placeName": "..."}
                            - OPEN_ITINERARY: {"itineraryId": 5, "title": "..."}
                            - OPEN_POST: {"postId": 8, "title": "..."}
                            - NAVIGATE: {"path": "/explore | /itineraries | /community | /profile"}

                            """ + "\n" + dbContext;

                    AiGenerateRequest aiReq = AiGenerateRequest.builder()
                            .model("gemini-3.5-flash-lite")
                            .systemPrompt(systemPrompt)
                            .history(historyTurns)
                            .prompt(userQuestion)
                            .temperature(0.4)
                            .maxTokens(8192)
                            .responseMimeType("application/json")
                            .build();

                    AiGenerateResponse aiRes = aiService.generateContent(aiReq);
                    String candidate = (aiRes != null && aiRes.getText() != null) ? aiRes.getText().trim() : "";
                    String parsedRich = normalizeAiRichJson(candidate);
                    if (parsedRich != null) {
                        rawResponseText = parsedRich;
                        finalMessageType = "AI_RICH";
                    } else if (!candidate.isBlank()) {
                        rawResponseText = extractSafePlainReply(candidate);
                    } else {
                        rawResponseText = "Mình đã ghi nhận yêu cầu của bạn. Bạn muốn mình tạo lịch trình hay tìm địa điểm cụ thể nào?";
                    }
                } else {
                    rawResponseText = "Cảm ơn bạn đã nhắn tin cho Trợ lý Wayfare! Hiện tại kết nối AI đang được bảo trì, bạn thử lại sau ít phút nhé.";
                }

                ChatMessage botMsg = ChatMessage.builder()
                        .chatRoom(freshRoom)
                        .sender(assistant)
                        .content(rawResponseText)
                        .messageType(finalMessageType)
                        .build();
                ChatMessage savedBotMsg = chatMessageRepository.save(botMsg);

                freshRoom.setUpdatedAt(LocalDateTime.now());
                chatRoomRepository.save(freshRoom);

                // Broadcast tin nhắn của Trợ lý AI qua WebSocket tới cả Web và Mobile
                ChatMessageDto botDto = mapToMessageDto(savedBotMsg, null);
                botDto.setIsRead(false);
                messagingTemplate.convertAndSend("/topic/room." + roomId, botDto);
                log.info("AI Assistant ({}) reply saved and broadcasted to room {}", finalMessageType, roomId);
            } catch (Exception e) {
                log.error("Failed to generate AI Assistant reply for room {}: {}", roomId, e.getMessage(), e);
            } finally {
                sendAiTypingEvent(roomId, assistant, false);
            }
        });
    }

    private String normalizeAiRichJson(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String cleaned = raw.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
            }
            int firstBrace = cleaned.indexOf('{');
            int lastBrace = cleaned.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                cleaned = cleaned.substring(firstBrace, lastBrace + 1);
            }
            JsonNode root = objectMapper.readTree(cleaned);
            if (root != null && root.isObject() && root.hasNonNull("reply")) {
                com.fasterxml.jackson.databind.node.ObjectNode obj = (com.fasterxml.jackson.databind.node.ObjectNode) root;
                // Dọn sạch dấu ** và mã kỹ thuật (placeId: 123) nếu AI lỡ chèn vào text hiển thị
                String replyStr = obj.get("reply").asText("")
                        .replace("**", "")
                        .replaceAll("\\s*\\((?:placeId|itineraryId|detailId|postId)\\s*:\\s*\\d+\\)", "")
                        .trim();
                obj.put("reply", replyStr);

                // Bảo đảm luôn có quickReplies để hiển thị chip gợi ý dưới tin nhắn
                if (!obj.has("quickReplies") || !obj.get("quickReplies").isArray() || obj.get("quickReplies").isEmpty()) {
                    com.fasterxml.jackson.databind.node.ArrayNode defaultQr = objectMapper.createArrayNode();
                    defaultQr.add("✨ Tạo lịch trình chi tiết hơn");
                    defaultQr.add("📋 Xem lịch trình của tôi");
                    defaultQr.add("🗺️ Gợi ý thêm địa điểm gần đây");
                    obj.set("quickReplies", defaultQr);
                }

                // Bảo đảm actions luôn là mảng hợp lệ
                if (!obj.has("actions") || !obj.get("actions").isArray()) {
                    obj.set("actions", objectMapper.createArrayNode());
                }
                return objectMapper.writeValueAsString(obj);
            }
        } catch (Exception e) {
            log.warn("Could not parse AI_RICH JSON, falling back to TEXT: {}", e.getMessage());
        }
        return null;
    }

    private String extractSafePlainReply(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String trimmed = raw.trim();
        // Nếu chuỗi bị cắt ngang nhưng vẫn có trường "reply": "..." thì trích ra cho sạch, không để lộ JSON thô
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"reply\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(trimmed);
        if (m.find()) {
            try {
                return objectMapper.readValue("\"" + m.group(1) + "\"", String.class).replace("**", "").trim();
            } catch (Exception ignored) {
            }
        }
        int braceIdx = trimmed.indexOf('{');
        if (braceIdx > 15) {
            return trimmed.substring(0, braceIdx).replace("**", "").trim();
        }
        if (trimmed.startsWith("{") || trimmed.contains("\"actions\"")) {
            return "Nội dung lịch trình hơi dài nên bị gián đoạn. Bạn thử yêu cầu số ngày ngắn hơn (3–5 ngày) để mình xuất đầy đủ chi tiết và nút lưu nhé!";
        }
        return trimmed.replace("**", "");
    }

    private String extractAiReplyPreview(String content) {
        if (content == null || content.isBlank()) return "Trợ lý Wayfare";
        try {
            JsonNode node = objectMapper.readTree(content);
            if (node != null && node.hasNonNull("reply")) {
                return node.get("reply").asText("Trợ lý Wayfare");
            }
        } catch (Exception ignored) {
        }
        return content;
    }

    @Transactional
    public synchronized ChatRoomDto getOrCreateDirectRoom(User currentUser, Long targetUserId) {
        log.info("Get or create direct room between user {} and user {}", currentUser.getId(), targetUserId);
        if (currentUser.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("Không thể tạo cuộc trò chuyện trực tiếp với chính mình.");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại: " + targetUserId));

        List<ChatRoom> existing = chatRoomRepository.findDirectRoomBetweenUsers(currentUser.getId(), targetUserId);
        if (!existing.isEmpty()) {
            log.info("Direct room already exists (id={}). Reusing existing room for persistence.", existing.get(0).getId());
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

        ChatRoomDto createdDto = mapToRoomDto(savedRoom, currentUser);

        // Phát sóng cho cả 2 người biết có phòng chat mới được tạo (realtime bên ngoài)
        try {
            messagingTemplate.convertAndSend("/topic/user." + currentUser.getId() + ".chat", createdDto);
            if (currentUser.getEmail() != null) {
                messagingTemplate.convertAndSend("/topic/user." + currentUser.getEmail().toLowerCase().trim() + ".chat", createdDto);
            }
            ChatRoomDto targetDto = mapToRoomDto(savedRoom, targetUser);
            messagingTemplate.convertAndSend("/topic/user." + targetUser.getId() + ".chat", targetDto);
            if (targetUser.getEmail() != null) {
                messagingTemplate.convertAndSend("/topic/user." + targetUser.getEmail().toLowerCase().trim() + ".chat", targetDto);
            }
        } catch (Exception ignored) {}

        return createdDto;
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

        Boolean roomIsOnline = false;
        String roomStatusText = "Ngoại tuyến";
        LocalDateTime roomLastActiveAt = null;
        boolean isAiAssistant = false;

        if ("DIRECT".equalsIgnoreCase(room.getType())) {
            // Hiển thị tên, avatar và trạng thái hoạt động thực tế của người đối diện
            Optional<User> other = members.stream()
                    .map(ChatMember::getUser)
                    .filter(u -> !u.getId().equals(currentUser.getId()))
                    .findFirst();

            if (other.isPresent()) {
                User otherUser = other.get();
                if (AI_ASSISTANT_EMAIL.equalsIgnoreCase(otherUser.getEmail())) {
                    isAiAssistant = true;
                    displayName = AI_ASSISTANT_NAME;
                    displayAvatar = AI_ASSISTANT_AVATAR;
                    roomIsOnline = true;
                    roomStatusText = "Trợ lý AI • Luôn trực tuyến";
                } else {
                    displayName = otherUser.getFullName() != null ? otherUser.getFullName() : otherUser.getEmail();
                    if (otherUser.getAvatarUrl() != null && !otherUser.getAvatarUrl().isBlank()) {
                        displayAvatar = otherUser.getAvatarUrl();
                    } else {
                        displayAvatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80";
                    }
                    roomIsOnline = isUserOnline(otherUser);
                    roomStatusText = formatLastActiveStatus(otherUser);
                    roomLastActiveAt = otherUser.getLastLoginAt();
                }
            }
        } else {
            // Nhóm trò chuyện: roomIsOnline = true nếu có ít nhất 1 thành viên khác đang online
            boolean hasOtherOnline = members.stream()
                    .map(ChatMember::getUser)
                    .filter(u -> !u.getId().equals(currentUser.getId()))
                    .anyMatch(this::isUserOnline);
            roomIsOnline = hasOtherOnline;
            roomStatusText = members.size() + " thành viên";
            if (room.getItinerary() != null && room.getItinerary().getCoverImageUrl() != null) {
                displayAvatar = room.getItinerary().getCoverImageUrl();
            }
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
            } else if ("AI_RICH".equalsIgnoreCase(msg.getMessageType())) {
                lastMsgText = extractAiReplyPreview(msg.getContent());
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
                .isOnline(isUserOnline(m.getUser()))
                .statusText(formatLastActiveStatus(m.getUser()))
                .lastActiveAt(m.getUser().getLastLoginAt())
                .lastReadMessageId(m.getLastReadMessageId())
                .build()
        ).collect(Collectors.toList());

        return ChatRoomDto.builder()
                .id(room.getId())
                .name(displayName)
                .type(isAiAssistant ? "AI" : room.getType())
                .itineraryId(room.getItinerary() != null ? room.getItinerary().getId() : null)
                .itineraryTitle(room.getItinerary() != null ? room.getItinerary().getTitle() : null)
                .membersCount(members.size())
                .avatar(displayAvatar)
                .lastMsg(lastMsgText)
                .time(timeStr)
                .unread((int) unread)
                .updatedAt(room.getUpdatedAt() != null ? room.getUpdatedAt() : room.getCreatedAt())
                .members(memberDtos)
                .isOnline(roomIsOnline)
                .statusText(roomStatusText)
                .lastActiveAt(roomLastActiveAt)
                .isPinned(isAiAssistant)
                .build();
    }

    private ChatMessageDto mapToMessageDto(ChatMessage m, User currentUser) {
        User sender = m.getSender();
        boolean isMe = currentUser != null && sender != null && sender.getId().equals(currentUser.getId());
        String avatar = sender != null && sender.getAvatarUrl() != null && !sender.getAvatarUrl().isBlank()
                ? sender.getAvatarUrl()
                : "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80";

        String senderDisplayName = sender != null
                ? (sender.getFullName() != null && !sender.getFullName().isBlank() ? sender.getFullName() : sender.getEmail())
                : "Thành viên";

        return ChatMessageDto.builder()
                .id(m.getId())
                .roomId(m.getChatRoom().getId())
                .senderId(sender != null ? sender.getId() : null)
                .senderEmail(sender != null ? sender.getEmail() : null)
                .senderName(senderDisplayName)
                .senderHandle(sender != null ? sender.getHandle() : null)
                .senderAvatar(avatar)
                .content(m.getContent())
                .messageType(m.getMessageType())
                .time(formatTime(m.getCreatedAt()))
                .createdAt(m.getCreatedAt())
                .isMe(currentUser != null ? isMe : null)
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

    private String removeAccents(String text) {
        if (text == null) return "";
        String normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "")
                .replace("đ", "d")
                .replace("Đ", "d")
                .toLowerCase()
                .trim();
    }

    public boolean isUserOnline(User user) {
        if (user == null || user.getLastLoginAt() == null) return false;
        // Người dùng được tính là trực tuyến nếu có hoạt động trong 10 phút gần nhất
        return Duration.between(user.getLastLoginAt(), LocalDateTime.now()).toMinutes() < 10;
    }

    public String formatLastActiveStatus(User user) {
        if (user == null || user.getLastLoginAt() == null) {
            return "Ngoại tuyến";
        }
        LocalDateTime lastLogin = user.getLastLoginAt();
        LocalDateTime now = LocalDateTime.now();
        long minutes = Duration.between(lastLogin, now).toMinutes();
        if (minutes < 10) {
            return "Đang hoạt động";
        } else if (minutes < 60) {
            return "Hoạt động " + minutes + " phút trước";
        } else {
            long hours = Duration.between(lastLogin, now).toHours();
            if (hours < 24) {
                return "Hoạt động " + hours + " giờ trước";
            } else {
                long days = Duration.between(lastLogin, now).toDays();
                if (days < 7) {
                    return "Hoạt động " + days + " ngày trước";
                } else {
                    return "Hoạt động ngày " + lastLogin.format(DATE_FMT);
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ChatMemberDto> getAvailableUsersForGroup(User currentUser, String keyword) {
        log.info("Fetching available users for group chat by: {}, keyword: {}", currentUser.getEmail(), keyword);
        List<User> users = userRepository.findAll();

        return users.stream()
                .filter(u -> !u.getId().equals(currentUser.getId()))
                .filter(u -> {
                    if (keyword == null || keyword.isBlank()) return true;
                    String kw = removeAccents(keyword);
                    String normName = removeAccents(u.getFullName());
                    String normEmail = removeAccents(u.getEmail());
                    String normHandle = removeAccents(u.getHandle());
                    return normName.contains(kw) || normEmail.contains(kw) || normHandle.contains(kw);
                })
                .limit(30)
                .map(u -> ChatMemberDto.builder()
                        .userId(u.getId())
                        .fullName(u.getFullName())
                        .handle(u.getHandle())
                        .email(u.getEmail())
                        .avatarUrl(u.getAvatarUrl() != null && !u.getAvatarUrl().isBlank()
                                ? u.getAvatarUrl()
                                : "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80")
                        .role("MEMBER")
                        .isOnline(isUserOnline(u))
                        .statusText(formatLastActiveStatus(u))
                        .lastActiveAt(u.getLastLoginAt())
                        .build()
                )
                .collect(Collectors.toList());
    }

    @Transactional
    public ChatRoomDto createGroupRoom(User currentUser, CreateRoomRequest request) {
        log.info("User {} creating new group chat room: {}", currentUser.getEmail(), request.getName());

        // Nghiệp vụ bắt buộc: 1 mình không thể tạo nhóm, phải chọn ít nhất 1 thành viên khác
        List<Long> otherMemberIds = (request.getMemberIds() != null)
                ? request.getMemberIds().stream().filter(uid -> !uid.equals(currentUser.getId())).distinct().collect(Collectors.toList())
                : Collections.emptyList();

        if (otherMemberIds.isEmpty()) {
            throw new IllegalArgumentException("Nhóm trò chuyện phải có ít nhất 1 thành viên khác cùng tham gia.");
        }

        String roomName = (request.getName() != null && !request.getName().isBlank())
                ? request.getName().trim()
                : "Nhóm thảo luận mới";

        ChatRoom newRoom = ChatRoom.builder()
                .name(roomName)
                .type("GROUP")
                .creator(currentUser)
                .build();
        ChatRoom saved = chatRoomRepository.save(newRoom);

        // Chủ phòng
        ChatMember owner = ChatMember.builder()
                .chatRoom(saved)
                .user(currentUser)
                .role("OWNER")
                .build();
        chatMemberRepository.save(owner);

        // Thêm các thành viên khác đã chọn
        for (Long uid : otherMemberIds) {
            userRepository.findById(uid).ifPresent(user -> {
                chatMemberRepository.save(ChatMember.builder()
                        .chatRoom(saved)
                        .user(user)
                        .role("MEMBER")
                        .build());
            });
        }

        // Tin nhắn khởi tạo nếu có
        String initText = (request.getInitialMessage() != null && !request.getInitialMessage().isBlank())
                ? request.getInitialMessage().trim()
                : "Chào mừng mọi người tham gia nhóm " + roomName + "!";

        ChatMessage initMsg = ChatMessage.builder()
                .chatRoom(saved)
                .sender(currentUser)
                .content(initText)
                .messageType("TEXT")
                .build();
        chatMessageRepository.save(initMsg);

        return mapToRoomDto(saved, currentUser);
    }

    @Transactional
    public void deleteRoom(Long roomId, User currentUser) {
        log.info("User {} deleting room {}", currentUser.getEmail(), roomId);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng chat không tồn tại: " + roomId));

        chatMessageRepository.deleteAll(chatMessageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId));
        chatMemberRepository.deleteAll(chatMemberRepository.findByChatRoomId(roomId));
        chatRoomRepository.delete(room);
        log.info("Successfully deleted room {}", roomId);
    }

    @Transactional(readOnly = true)
    public List<ChatMemberDto> getRoomMembers(Long roomId, User currentUser) {
        log.info("Fetching members for room {} by user {}", roomId, currentUser.getEmail());
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng chat không tồn tại: " + roomId));

        boolean isMember = chatMemberRepository.existsByChatRoomIdAndUserId(roomId, currentUser.getId());
        if (!isMember) {
            throw new IllegalArgumentException("Bạn không có quyền xem danh sách thành viên của phòng này.");
        }

        return chatMemberRepository.findByChatRoomId(roomId).stream()
                .map(m -> ChatMemberDto.builder()
                        .userId(m.getUser().getId())
                        .fullName(m.getUser().getFullName())
                        .handle(m.getUser().getHandle())
                        .email(m.getUser().getEmail())
                        .avatarUrl(m.getUser().getAvatarUrl() != null && !m.getUser().getAvatarUrl().isBlank()
                                ? m.getUser().getAvatarUrl()
                                : "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80")
                        .role(m.getRole())
                        .joinedAt(m.getJoinedAt() != null ? formatTime(m.getJoinedAt()) : "")
                        .isOnline(isUserOnline(m.getUser()))
                        .statusText(formatLastActiveStatus(m.getUser()))
                        .lastActiveAt(m.getUser().getLastLoginAt())
                        .lastReadMessageId(m.getLastReadMessageId())
                        .build()
                )
                .collect(Collectors.toList());
    }

    @Transactional
    public ChatRoomDto addMembersToRoom(Long roomId, User currentUser, AddMembersRequest request) {
        log.info("User {} adding members to room {}: {}", currentUser.getEmail(), roomId, request != null ? request.getMemberIds() : null);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng chat không tồn tại: " + roomId));

        if ("DIRECT".equalsIgnoreCase(room.getType())) {
            throw new IllegalArgumentException("Không thể thêm thành viên vào cuộc trò chuyện cá nhân 1-1.");
        }

        boolean isMember = chatMemberRepository.existsByChatRoomIdAndUserId(roomId, currentUser.getId());
        if (!isMember) {
            throw new IllegalArgumentException("Bạn không phải thành viên của nhóm này.");
        }

        List<Long> memberIdsToAdd = (request != null && request.getMemberIds() != null)
                ? request.getMemberIds()
                : Collections.emptyList();

        if (memberIdsToAdd.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất 1 thành viên để thêm vào nhóm.");
        }

        List<String> addedNames = new ArrayList<>();
        for (Long uid : memberIdsToAdd) {
            if (!chatMemberRepository.existsByChatRoomIdAndUserId(roomId, uid)) {
                userRepository.findById(uid).ifPresent(user -> {
                    chatMemberRepository.save(ChatMember.builder()
                            .chatRoom(room)
                            .user(user)
                            .role("MEMBER")
                            .build());
                    addedNames.add(user.getFullName() != null ? user.getFullName() : user.getEmail());
                });
            }
        }

        if (!addedNames.isEmpty()) {
            String sysContent = currentUser.getFullName() + " đã thêm " + String.join(", ", addedNames) + " vào nhóm.";
            ChatMessage sysMsg = ChatMessage.builder()
                    .chatRoom(room)
                    .sender(currentUser)
                    .content(sysContent)
                    .messageType("SYSTEM")
                    .build();
            ChatMessage saved = chatMessageRepository.save(sysMsg);
            room.setUpdatedAt(LocalDateTime.now());
            chatRoomRepository.save(room);

            ChatMessageDto msgDto = mapToMessageDto(saved, null);
            try {
                messagingTemplate.convertAndSend("/topic/room." + roomId, msgDto);
            } catch (Exception e) {
                log.warn("Failed to broadcast member added message: {}", e.getMessage());
            }
        }

        return mapToRoomDto(room, currentUser);
    }

    @Transactional
    public ChatRoomDto removeMemberFromRoom(Long roomId, User currentUser, Long targetUserId) {
        log.info("User {} removing member {} from room {}", currentUser.getEmail(), targetUserId, roomId);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng chat không tồn tại: " + roomId));

        if ("DIRECT".equalsIgnoreCase(room.getType())) {
            throw new IllegalArgumentException("Không thể xóa thành viên khỏi cuộc trò chuyện cá nhân 1-1.");
        }

        ChatMember myMember = chatMemberRepository.findByChatRoomIdAndUserId(roomId, currentUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Bạn không thuộc nhóm trò chuyện này."));

        ChatMember targetMember = chatMemberRepository.findByChatRoomIdAndUserId(roomId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Thành viên không tồn tại trong nhóm này: " + targetUserId));

        boolean isSelfLeave = currentUser.getId().equals(targetUserId);

        // Nếu kick người khác, người thực hiện phải là OWNER hoặc creator của phòng
        if (!isSelfLeave) {
            boolean isOwner = "OWNER".equalsIgnoreCase(myMember.getRole()) ||
                    (room.getCreator() != null && room.getCreator().getId().equals(currentUser.getId()));
            if (!isOwner) {
                throw new IllegalArgumentException("Chỉ trưởng nhóm (Admin) mới có quyền kick thành viên khỏi nhóm.");
            }
        }

        String targetName = targetMember.getUser().getFullName() != null ? targetMember.getUser().getFullName() : targetMember.getUser().getEmail();
        chatMemberRepository.delete(targetMember);

        // Kiểm tra số lượng thành viên còn lại
        List<ChatMember> remainingMembers = chatMemberRepository.findByChatRoomId(roomId);
        if (remainingMembers.isEmpty()) {
            chatMessageRepository.deleteAll(chatMessageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId));
            chatRoomRepository.delete(room);
            log.info("No members left in room {}. Room successfully purged.", roomId);
            return null;
        }

        // Nếu người rời nhóm là OWNER, trao quyền OWNER cho thành viên còn lại
        if (isSelfLeave && "OWNER".equalsIgnoreCase(targetMember.getRole())) {
            ChatMember nextOwner = remainingMembers.get(0);
            nextOwner.setRole("OWNER");
            chatMemberRepository.save(nextOwner);
            log.info("Transferred OWNER role of room {} to user {}", roomId, nextOwner.getUser().getId());
        }

        // Tạo tin nhắn hệ thống ghi nhận hành động
        String sysContent = isSelfLeave
                ? targetName + " đã rời khỏi nhóm trò chuyện."
                : (currentUser.getFullName() != null ? currentUser.getFullName() : currentUser.getEmail()) + " đã xóa " + targetName + " khỏi nhóm.";

        ChatMessage sysMsg = ChatMessage.builder()
                .chatRoom(room)
                .sender(currentUser)
                .content(sysContent)
                .messageType("SYSTEM")
                .build();
        ChatMessage saved = chatMessageRepository.save(sysMsg);
        room.setUpdatedAt(LocalDateTime.now());
        chatRoomRepository.save(room);

        ChatMessageDto msgDto = mapToMessageDto(saved, null);
        try {
            messagingTemplate.convertAndSend("/topic/room." + roomId, msgDto);
        } catch (Exception e) {
            log.warn("Failed to broadcast member removal message: {}", e.getMessage());
        }

        return mapToRoomDto(room, currentUser);
    }

    @Transactional(readOnly = true)
    public long getTotalUnreadCount(User user) {
        if (user == null || user.getId() == null) return 0;
        List<ChatMember> memberships = chatMemberRepository.findByUserId(user.getId());
        long total = 0;
        for (ChatMember member : memberships) {
            if (member.getChatRoom() != null && member.getChatRoom().getId() != null) {
                long unread = chatMessageRepository.countUnreadMessages(member.getChatRoom().getId(), member.getLastReadMessageId());
                total += unread;
            }
        }
        return total;
    }

    @Transactional
    public Long markRoomAsRead(Long roomId, User currentUser) {
        log.info("User {} marking room {} as read", currentUser.getEmail(), roomId);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng chat không tồn tại: " + roomId));

        ChatMember member = chatMemberRepository.findByChatRoomIdAndUserId(roomId, currentUser.getId())
                .orElseGet(() -> {
                    if ("DIRECT".equalsIgnoreCase(room.getType())) {
                        if (room.getCreator() != null && room.getCreator().getId().equals(currentUser.getId())) {
                            log.info("Restoring missing OWNER member for creator {} in DIRECT room {}", currentUser.getEmail(), roomId);
                            ChatMember restored = ChatMember.builder()
                                    .chatRoom(room)
                                    .user(currentUser)
                                    .role("OWNER")
                                    .build();
                            return chatMemberRepository.save(restored);
                        }
                        log.warn("Blocked unauthorized attempt to mark read in DIRECT room {} by user {}", roomId, currentUser.getEmail());
                        throw new ResourceNotFoundException("Bạn không phải thành viên của cuộc trò chuyện 1-1 này.");
                    }
                    ChatMember newMember = ChatMember.builder()
                            .chatRoom(room)
                            .user(currentUser)
                            .role("MEMBER")
                            .build();
                    return chatMemberRepository.save(newMember);
                });

        List<ChatMessage> messages = chatMessageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId);
        if (messages.isEmpty()) {
            return 0L;
        }

        ChatMessage latest = messages.get(messages.size() - 1);
        Long latestId = latest.getId();
        if (member.getLastReadMessageId() == null || member.getLastReadMessageId() < latestId) {
            member.setLastReadMessageId(latestId);
            chatMemberRepository.save(member);
            log.info("Updated lastReadMessageId for user {} in room {} to {}", currentUser.getEmail(), roomId, latestId);
        }

        // Phát sóng sự kiện đã đọc qua WebSocket /topic/room.{roomId}/read
        try {
            Map<String, Object> receipt = Map.of(
                    "roomId", roomId,
                    "userId", currentUser.getId(),
                    "email", currentUser.getEmail(),
                    "userName", currentUser.getFullName() != null ? currentUser.getFullName() : currentUser.getEmail(),
                    "lastReadMessageId", latestId
            );
            messagingTemplate.convertAndSend("/topic/room." + roomId + "/read", receipt);
            log.info("Broadcasted read receipt for room {} to /topic/room.{}/read", roomId, roomId);

            // Cập nhật trạng thái unread cho user bên ngoài realtime
            ChatRoomDto readRoomDto = mapToRoomDto(room, currentUser);
            messagingTemplate.convertAndSend("/topic/user." + currentUser.getId() + ".chat", readRoomDto);
            if (currentUser.getEmail() != null) {
                messagingTemplate.convertAndSend("/topic/user." + currentUser.getEmail().toLowerCase().trim() + ".chat", readRoomDto);
            }
        } catch (Exception e) {
            log.warn("Failed to broadcast read receipt for room {}: {}", roomId, e.getMessage());
        }

        return latestId;
    }
}
