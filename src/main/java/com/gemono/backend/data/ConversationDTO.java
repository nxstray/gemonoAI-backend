package com.gemono.backend.data;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class ConversationDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private UUID id;
        private String title;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Detail {
        private UUID id;
        private String title;
        private List<MessageDTO.Response> messages;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // Request body for PATCH /conversations/{id} — renames the conversation title
    @Data
    public static class RenameRequest {
        @NotBlank
        private String title;
    }
}