package com.gemono.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

// Stores one-time magic link tokens for passwordless email login
@Entity
@Table(name = "magic_link_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MagicLinkToken {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    // The email this token was sent to
    @Column(nullable = false)
    private String email;

    // UUID token included in the magic link URL
    @Column(nullable = false, unique = true)
    private String token;

    // Token expires after N minutes (set in properties)
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // Consumed = already used — prevent replay attacks
    @Column(nullable = false)
    private boolean consumed;

    // Optional: guest ID to merge after verify
    @Column(name = "guest_id")
    private String guestId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        consumed = false;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}