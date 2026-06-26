package com.gemono.backend.controller;

import com.gemono.backend.data.*;
import com.gemono.backend.service.GuestService;
import com.gemono.backend.data.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.gemono.backend.service.FileService;

import java.util.List;
import java.util.UUID;

// Endpoints for guest (unauthenticated) users — identified by guestId header
@RestController
@RequestMapping("/api/guest")
@RequiredArgsConstructor
@Tag(name = "Guest", description = "Guest session chat — no auth required")
public class GuestController {

    private final GuestService guestService;
    private final FileService fileService;

    @GetMapping("/conversations")
    @Operation(summary = "List guest conversations")
    public ResponseEntity<ApiResponse<List<ConversationDTO.Summary>>> list(
            @RequestHeader("X-Guest-Id") String guestId) {
        return ResponseEntity.ok(ApiResponse.ok(guestService.getConversations(guestId)));
    }

    @GetMapping("/conversations/{id}")
    @Operation(summary = "Get guest conversation detail")
    public ResponseEntity<ApiResponse<ConversationDTO.Detail>> detail(
            @RequestHeader("X-Guest-Id") String guestId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(guestService.getConversationDetail(guestId, id)));
    }

    @PostMapping("/conversations/send")
    @Operation(summary = "Send message as guest — triggers agentic AI response")
    public ResponseEntity<ApiResponse<MessageDTO.Response>> send(
            @RequestHeader("X-Guest-Id") String guestId,
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
                guestService.sendMessage(guestId, conversationId, content, attachmentUrl, attachmentType)));
    }

    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "Delete guest conversation")
    public ResponseEntity<ApiResponse<Void>> delete(
            @RequestHeader("X-Guest-Id") String guestId,
            @PathVariable UUID id) {
        guestService.deleteConversation(guestId, id);
        return ResponseEntity.ok(ApiResponse.ok("Deleted"));
    }
}