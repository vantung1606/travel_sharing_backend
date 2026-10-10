package com.wayfare.repository;

import com.wayfare.entity.ChatMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMemberRepository extends JpaRepository<ChatMember, Long> {

    List<ChatMember> findByChatRoomId(Long roomId);

    Optional<ChatMember> findByChatRoomIdAndUserId(Long roomId, Long userId);

    boolean existsByChatRoomIdAndUserId(Long roomId, Long userId);

    List<ChatMember> findByUserId(Long userId);
}
