package com.wayfare.modules.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomDto {
    private Long id;
    private String name;
    private String type; // DIRECT, GROUP
    private Long itineraryId;
    private String itineraryTitle;
    private Integer membersCount;
    private String avatar;
    private String lastMsg;
    private String time;
    private Integer unread;
    private LocalDateTime updatedAt;
    private List<ChatMemberDto> members;
}
