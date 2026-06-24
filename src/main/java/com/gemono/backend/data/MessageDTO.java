package com.gemono.backend.data;

import lombok.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

public class MessageDTO {

    @Data
    public static class SendRequest {
        @NotBlank
        private String content;
        // Optional: conversation ID (null = start new conversation)
        private Long conversationId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String role;
        private String content;
        private String attachmentUrl;
        private String attachmentType;
        private List<AgentStepDTO> agentSteps;
        private LocalDateTime createdAt;
    }
}