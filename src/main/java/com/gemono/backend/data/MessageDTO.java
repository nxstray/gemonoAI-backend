package com.gemono.backend.data;

import lombok.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class MessageDTO {

    @Data
    public static class SendRequest {
        @NotBlank
        private String content;
        // Optional: conversation ID (null = start new conversation)
        private UUID conversationId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private UUID id;
        private String role;
        private String content;
        private String attachmentUrl;
        private String attachmentType;
        private List<AgentStepDTO> agentSteps;
        private LocalDateTime createdAt;
    }
}