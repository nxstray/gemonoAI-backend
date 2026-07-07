package com.gemono.backend.controller;

import com.gemono.backend.data.ApiResponse;
import com.gemono.backend.data.AttachmentDTO;
import com.gemono.backend.data.ConversationDTO;
import com.gemono.backend.data.MessageDTO;
import com.gemono.backend.service.GuestService;
import com.gemono.backend.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
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
            @RequestParam(required = false) List<MultipartFile> files) throws Exception {

        if (guestId == null || guestId.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Missing required header: X-Guest-Id"));
        }

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
                guestService.sendMessage(guestId, conversationId, content, attachments)));
    }

    @PostMapping(value = "/conversations/send/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Send message as guest — streams the AI response as Server-Sent Events")
    public SseEmitter sendStream(
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId,
            @RequestParam String content,
            @RequestParam(required = false) UUID conversationId,
            @RequestParam(required = false) List<MultipartFile> files) throws Exception {

        if (guestId == null || guestId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required header: X-Guest-Id");
        }

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

        SseEmitter emitter = new SseEmitter(0L);
        guestService.sendMessageStream(guestId, conversationId, content, attachments, emitter);
        return emitter;
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