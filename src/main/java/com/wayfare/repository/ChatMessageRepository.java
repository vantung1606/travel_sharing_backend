package com.wayfare.repository;

import com.wayfare.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByChatRoomIdOrderByCreatedAtAsc(Long roomId);

    List<ChatMessage> findByChatRoomIdOrderByCreatedAtDesc(Long roomId, Pageable pageable);

    Optional<ChatMessage> findTop1ByChatRoomIdOrderByCreatedAtDesc(Long roomId);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.chatRoom.id = :roomId AND (:lastReadId IS NULL OR m.id > :lastReadId)")
    long countUnreadMessages(@Param("roomId") Long roomId, @Param("lastReadId") Long lastReadId);
}
