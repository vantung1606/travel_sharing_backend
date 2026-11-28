package com.wayfare.modules.chat.service;

import com.wayfare.entity.ChatRoom;
import com.wayfare.entity.User;
import com.wayfare.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {
    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock ChatMemberRepository chatMemberRepository;
    @Mock UserRepository userRepository;
    @Mock ItineraryRepository itineraryRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks ChatService chatService;

    private User user(long id) {
        return User.builder().id(id).email("test" + id + "@example.invalid").fullName("User " + id).build();
    }

    @Test
    void openingDirectRoomDoesNotSendAMessageOnBehalfOfTheUser() {
        User sender = user(1), recipient = user(2);
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
        when(chatRoomRepository.findDirectRoomBetweenUsers(1L, 2L)).thenReturn(List.of());
        when(chatRoomRepository.save(any())).thenAnswer(invocation -> {
            ChatRoom room = invocation.getArgument(0);
            room.setId(9L);
            return room;
        });

        chatService.getOrCreateDirectRoom(sender, 2L);

        verify(chatMemberRepository).saveAll(anyList());
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void nonMemberCannotDeleteRoomOrItsMessages() {
        when(chatRoomRepository.findById(9L)).thenReturn(Optional.of(ChatRoom.builder().id(9L).build()));

        assertThrows(IllegalArgumentException.class, () -> chatService.deleteRoom(9L, user(1)));

        verifyNoInteractions(chatMessageRepository);
        verify(chatRoomRepository, never()).delete(any());
    }

    @Test
    void deletingRoomNeverCreatesAnotherRoomOrGreeting() {
        ChatRoom room = ChatRoom.builder().id(9L).build();
        when(chatRoomRepository.findById(9L)).thenReturn(Optional.of(room));
        when(chatMemberRepository.existsByChatRoomIdAndUserId(9L, 1L)).thenReturn(true);

        chatService.deleteRoom(9L, user(1));

        verify(chatRoomRepository).delete(room);
        verify(chatRoomRepository, never()).save(any());
        verify(chatMessageRepository, never()).save(any());
    }
}
