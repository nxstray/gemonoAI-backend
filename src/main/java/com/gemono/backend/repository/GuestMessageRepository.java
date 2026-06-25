package com.gemono.backend.repository;

import com.gemono.backend.model.GuestMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuestMessageRepository extends JpaRepository<GuestMessage, Long> {
    List<GuestMessage> findByGuestConversationIdOrderByCreatedAtAsc(Long guestConversationId);
}