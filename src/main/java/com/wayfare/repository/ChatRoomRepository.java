package com.wayfare.repository;

import com.wayfare.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByItineraryId(Long itineraryId);

    @Query("SELECT r FROM ChatRoom r JOIN ChatMember m ON m.chatRoom = r WHERE m.user.id = :userId ORDER BY r.updatedAt DESC")
    List<ChatRoom> findRoomsByUserId(@Param("userId") Long userId);

    @Query("SELECT r FROM ChatRoom r WHERE r.type = 'DIRECT' AND r.id IN " +
           "(SELECT m1.chatRoom.id FROM ChatMember m1 WHERE m1.user.id = :user1Id) AND r.id IN " +
           "(SELECT m2.chatRoom.id FROM ChatMember m2 WHERE m2.user.id = :user2Id)")
    List<ChatRoom> findDirectRoomBetweenUsers(@Param("user1Id") Long user1Id, @Param("user2Id") Long user2Id);
}
