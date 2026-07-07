package com.gemono.backend.controller;

import com.gemono.backend.data.*;
import com.gemono.backend.service.ConversationService;
import com.gemono.backend.service.FileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;

import java.util.ArrayList;
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
            @RequestParam(required = false) List<MultipartFile> files) throws Exception {

        // Save every attached file and build the attachment list — replaces the old single-file logic
        List<AttachmentDTO> attachments = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    String url = fileService.saveFile(file);
                    String type = fileService.getFileType(file);
                    attachments.add(AttachmentDTO.builder()
                            .url(url)
                            .type(type)
                            .name(file.getOriginalFilename())
                            .build());
                }
            }
        }

        return ResponseEntity.ok(ApiResponse.ok("Response generated",
                conversationService.sendMessage(email, conversationId, content, attachments)));
    }

    @PostMapping(value = "/send/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Send message — streams the AI response as Server-Sent Events")
    public SseEmitter sendStream(
            @AuthenticationPrincipal String email,
            @RequestParam String content,
            @RequestParam(required = false) UUID conversationId,
            @RequestParam(required = false) List<MultipartFile> files) throws Exception {

        List<AttachmentDTO> attachments = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    String url = fileService.saveFile(file);
                    String type = fileService.getFileType(file);
                    attachments.add(AttachmentDTO.builder()
                            .url(url)
                            .type(type)
                            .name(file.getOriginalFilename())
                            .build());
                }
            }
        }

        SseEmitter emitter = new SseEmitter(0L); // no timeout — Groq generations can take a while
        conversationService.sendMessageStream(email, conversationId, content, attachments, emitter);
        return emitter;
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a conversation")
    public ResponseEntity<ApiResponse<ConversationDTO.Summary>> rename(
            @AuthenticationPrincipal String email,
            @PathVariable UUID id,
            @RequestBody ConversationDTO.RenameRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(conversationService.renameConversation(email, id, request.getTitle())));
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