package com.gemono.backend.repository;

import com.gemono.backend.model.GuestMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GuestMessageRepository extends JpaRepository<GuestMessage, UUID> {
    List<GuestMessage> findByGuestConversationIdOrderByCreatedAtAsc(UUID guestConversationId);
}