package com.gemono.backend.repository;

import com.gemono.backend.model.GuestConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuestConversationRepository extends JpaRepository<GuestConversation, Long> {
    List<GuestConversation> findByGuestIdAndMergedToUserIsNullOrderByUpdatedAtDesc(String guestId);
    List<GuestConversation> findByGuestIdAndMergedToUserIsNull(String guestId);
}