package com.gemono.backend.repository;

import com.gemono.backend.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);
}