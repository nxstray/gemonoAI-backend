package com.gemono.backend.controller;

import com.gemono.backend.data.*;
import com.gemono.backend.service.ConversationService;
import com.gemono.backend.service.FileService;
import com.gemono.backend.data.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Tag(name = "Conversations", description = "Chat conversation management")
public class ConversationController {

    private final ConversationService conversationService;
    private final FileService fileService;

    @GetMapping
    @Operation(summary = "Get all conversations for current user")
    public ResponseEntity<ApiResponse<List<ConversationDTO.Summary>>> list(
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(ApiResponse.ok(conversationService.getConversations(email)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get conversation details with messages")
    public ResponseEntity<ApiResponse<ConversationDTO.Detail>> detail(
            @AuthenticationPrincipal String email,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(conversationService.getConversationDetail(email, id)));
    }

    @PostMapping("/send")
    @Operation(summary = "Send message — triggers agentic AI response")
    public ResponseEntity<ApiResponse<MessageDTO.Response>> send(
            @AuthenticationPrincipal String email,
            @RequestParam String content,
            @RequestParam(required = false) UUID conversationId,
            @RequestParam(required = false) MultipartFile file) throws Exception {

        String attachmentUrl = null;
        String attachmentType = null;

        if (file != null && !file.isEmpty()) {
            attachmentUrl = fileService.saveFile(file);
            attachmentType = fileService.getFileType(file);
        }

        return ResponseEntity.ok(ApiResponse.ok("Response generated",
                conversationService.sendMessage(email, conversationId, content, attachmentUrl, attachmentType)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a conversation")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal String email,
            @PathVariable UUID id) {
        conversationService.deleteConversation(email, id);
        return ResponseEntity.ok(ApiResponse.ok("Conversation deleted"));
    }
}