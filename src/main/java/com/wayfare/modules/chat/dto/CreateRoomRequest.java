package com.wayfare.modules.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomRequest {
    private String name;
    private String type; // DIRECT, GROUP
    private Long itineraryId;
    private Long targetUserId;
    private List<Long> memberIds;
    private String initialMessage;
}
