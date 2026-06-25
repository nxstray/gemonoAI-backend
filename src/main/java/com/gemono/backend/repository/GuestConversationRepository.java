package com.gemono.backend.repository;

import com.gemono.backend.model.GuestConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
public interface GuestConversationRepository extends JpaRepository<GuestConversation, UUID> {
    List<GuestConversation> findByGuestIdAndMergedToUserIsNullOrderByUpdatedAtDesc(String guestId);
    List<GuestConversation> findByGuestIdAndMergedToUserIsNull(String guestId);
}