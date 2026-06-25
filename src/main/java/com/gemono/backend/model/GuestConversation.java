package com.gemono.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Stores conversations for unauthenticated guests identified by a UUID
@Entity
@Table(name = "guest_conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Browser-generated UUID stored in localStorage
    @Column(name = "guest_id", nullable = false)
    private String guestId;

    @Column(nullable = false)
    private String title;

    @OneToMany(mappedBy = "guestConversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<GuestMessage> messages = new ArrayList<>();

    // Set when guest logs in and history is merged
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merged_to_user_id")
    private User mergedToUser;

    @Column(name = "merged_at")
    private LocalDateTime mergedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}