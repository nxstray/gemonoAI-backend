package com.gemono.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    // "user" or "assistant"
    @Column(nullable = false)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // Optional: path to attached file/image
    @Column(name = "attachment_url")
    private String attachmentUrl;

    @Column(name = "attachment_type")
    private String attachmentType;

    // Multiple attachments serialized as a JSON array (see AttachmentDTO) — supports
    // sending more than one file per message. attachmentUrl/attachmentType above are
    // kept for backward compatibility with older messages that only had one file
    @Column(name = "attachments", columnDefinition = "TEXT")
    private String attachments;

    // Steps taken by agent (serialized JSON string)
    @Column(name = "agent_steps", columnDefinition = "TEXT")
    private String agentSteps;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}