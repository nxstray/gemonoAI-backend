package com.gemono.backend.controller;

import com.gemono.backend.data.ApiResponse;
import com.gemono.backend.data.ConversationDTO;
import com.gemono.backend.data.MessageDTO;
import com.gemono.backend.service.GuestService;
import com.gemono.backend.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

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
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId) {
        
        if (guestId == null || guestId.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Missing required header: X-Guest-Id"));
        }
        
        return ResponseEntity.ok(ApiResponse.ok(guestService.getConversations(guestId)));
    }

    @GetMapping("/conversations/{id}")
    @Operation(summary = "Get guest conversation detail")
    public ResponseEntity<ApiResponse<ConversationDTO.Detail>> detail(
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId,
            @PathVariable UUID id) {
        
        if (guestId == null || guestId.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Missing required header: X-Guest-Id"));
        }
        
        return ResponseEntity.ok(ApiResponse.ok(guestService.getConversationDetail(guestId, id)));
    }

    @PostMapping("/conversations/send")
    @Operation(summary = "Send message as guest — triggers agentic AI response")
    public ResponseEntity<ApiResponse<MessageDTO.Response>> send(
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId,
            @RequestParam String content,
            @RequestParam(required = false) UUID conversationId,
            @RequestParam(required = false) MultipartFile file) throws Exception {

        if (guestId == null || guestId.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Missing required header: X-Guest-Id"));
        }

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
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId,
            @PathVariable UUID id) {
        
        if (guestId == null || guestId.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Missing required header: X-Guest-Id"));
        }
        
        guestService.deleteConversation(guestId, id);
        return ResponseEntity.ok(ApiResponse.ok("Deleted"));
    }
}