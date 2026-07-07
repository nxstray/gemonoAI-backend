package com.gemono.backend.service;

import com.gemono.backend.data.*;
import com.gemono.backend.model.*;
import com.gemono.backend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

// Handles all guest (unauthenticated) chat logic and merge-on-login
@Service
@RequiredArgsConstructor
public class GuestService {

    private final GuestConversationRepository guestConvRepo;
    private final GuestMessageRepository guestMsgRepo;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserService userService;
    private final AgentService agentService;
    private final ObjectMapper objectMapper; // used to serialize/deserialize the attachments list

    // Guest sends a message — creates conversation if needed, runs agent.
    // Now accepts a list of attachments instead of a single attachmentUrl/attachmentType pair.
    @Transactional
    public MessageDTO.Response sendMessage(String guestId, UUID conversationId,
                                            String content, List<AttachmentDTO> attachments) {

        List<AttachmentDTO> safeAttachments = attachments != null ? attachments : List.of();

        GuestConversation conversation;
        if (conversationId != null) {
            conversation = guestConvRepo.findById(conversationId)
                    .orElseThrow(() -> new RuntimeException("Conversation not found"));
        } else {
            String title = content.length() > 50 ? content.substring(0, 50) + "..." : content;
            conversation = GuestConversation.builder()
                    .guestId(guestId)
                    .title(title)
                    .build();
            conversation = guestConvRepo.save(conversation);
        }

        // First attachment mirrored into the legacy single-attachment columns
        AttachmentDTO firstAttachment = safeAttachments.isEmpty() ? null : safeAttachments.get(0);

        // Save user message
        GuestMessage userMsg = GuestMessage.builder()
                .guestConversation(conversation)
                .role("user")
                .content(content)
                .attachmentUrl(firstAttachment != null ? firstAttachment.getUrl() : null)
                .attachmentType(firstAttachment != null ? firstAttachment.getType() : null)
                .attachments(serializeAttachments(safeAttachments))
                .build();
        guestMsgRepo.save(userMsg);

        // Build history for agent context
        List<GuestMessage> history = guestMsgRepo
        .findByGuestConversationIdOrderByCreatedAtAsc(conversation.getId());
                List<GuestMessage> recentHistory = history.size() > 10
                        ? history.subList(history.size() - 10, history.size())
                        : history;
                List<Map<String, String>> historyMaps = recentHistory.stream()
                        .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                        .collect(Collectors.toList());

        // Run agent
        AgentService.AgentResult result = agentService.run(content, historyMaps, safeAttachments);

        GuestMessage assistantMsg = GuestMessage.builder()
                .guestConversation(conversation)
                .role("assistant")
                .content(result.answer())
                .agentSteps(agentService.serializeSteps(result.steps()))
                .build();
        guestMsgRepo.save(assistantMsg);

        guestConvRepo.save(conversation);

        return toDto(assistantMsg, result.steps());
    }

    public List<ConversationDTO.Summary> getConversations(String guestId) {
        return guestConvRepo
                .findByGuestIdAndMergedToUserIsNullOrderByUpdatedAtDesc(guestId)
                .stream()
                .map(c -> ConversationDTO.Summary.builder()
                        .id(c.getId())
                        .title(c.getTitle())
                        .updatedAt(c.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public ConversationDTO.Detail getConversationDetail(String guestId, UUID conversationId) {
        GuestConversation conv = guestConvRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Not found"));

        if (!conv.getGuestId().equals(guestId)) {
            throw new RuntimeException("Access denied");
        }

        List<GuestMessage> messages = guestMsgRepo
                .findByGuestConversationIdOrderByCreatedAtAsc(conversationId);

        return ConversationDTO.Detail.builder()
                .id(conv.getId())
                .title(conv.getTitle())
                .messages(messages.stream()
                        .map(m -> toDto(m, agentService.deserializeSteps(m.getAgentSteps())))
                        .collect(Collectors.toList()))
                .createdAt(conv.getCreatedAt())
                .updatedAt(conv.getUpdatedAt())
                .build();
    }

    // Merge all guest conversations into the user's account after login
    @Transactional
    public int mergeGuestHistory(String guestId, String userEmail) {
        List<GuestConversation> guestConvs = guestConvRepo
                .findByGuestIdAndMergedToUserIsNull(guestId);

        if (guestConvs.isEmpty()) return 0;

        User user = userService.findByEmail(userEmail);

        for (GuestConversation guestConv : guestConvs) {
            // Create real conversation
            Conversation realConv = Conversation.builder()
                    .user(user)
                    .title(guestConv.getTitle())
                    .build();
            realConv = conversationRepository.save(realConv);

            // Copy all messages
            List<GuestMessage> guestMessages = guestMsgRepo
                    .findByGuestConversationIdOrderByCreatedAtAsc(guestConv.getId());

            for (GuestMessage gm : guestMessages) {
                Message msg = Message.builder()
                        .conversation(realConv)
                        .role(gm.getRole())
                        .content(gm.getContent())
                        .attachmentUrl(gm.getAttachmentUrl())
                        .attachmentType(gm.getAttachmentType())
                        .attachments(gm.getAttachments()) // carry over the multi-attachment JSON as-is
                        .agentSteps(gm.getAgentSteps())
                        .build();
                messageRepository.save(msg);
            }

            // Mark guest conversation as merged
            guestConv.setMergedToUser(user);
            guestConv.setMergedAt(LocalDateTime.now());
            guestConvRepo.save(guestConv);
        }

        return guestConvs.size();
    }

    // Streaming variant of sendMessage() for guests — same pattern as
    // ConversationService.sendMessageStream()
    public void sendMessageStream(String guestId, UUID conversationId, String content,
                                   List<AttachmentDTO> attachments, SseEmitter emitter) {

        List<AttachmentDTO> safeAttachments = attachments != null ? attachments : List.of();

        GuestConversation conversation;
        if (conversationId != null) {
            conversation = guestConvRepo.findById(conversationId)
                    .orElseThrow(() -> new RuntimeException("Conversation not found"));
        } else {
            String title = content.length() > 50 ? content.substring(0, 50) + "..." : content;
            GuestConversation newConversation = GuestConversation.builder()
                    .guestId(guestId)
                    .title(title)
                    .build();
            conversation = guestConvRepo.save(newConversation);
        }
        final GuestConversation finalConversation = conversation;
        final UUID resolvedConversationId = finalConversation.getId();

        AttachmentDTO firstAttachment = safeAttachments.isEmpty() ? null : safeAttachments.get(0);
        GuestMessage userMsg = GuestMessage.builder()
                .guestConversation(finalConversation)
                .role("user")
                .content(content)
                .attachmentUrl(firstAttachment != null ? firstAttachment.getUrl() : null)
                .attachmentType(firstAttachment != null ? firstAttachment.getType() : null)
                .attachments(serializeAttachments(safeAttachments))
                .build();
        guestMsgRepo.save(userMsg);

        List<GuestMessage> history = guestMsgRepo
        .findByGuestConversationIdOrderByCreatedAtAsc(resolvedConversationId);
        List<GuestMessage> recentHistory = history.size() > 10
                ? history.subList(history.size() - 10, history.size())
                : history;
        List<Map<String, String>> historyMaps = recentHistory.stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList());
                StringBuilder fullAnswer = new StringBuilder();

        agentService.runStream(content, historyMaps, safeAttachments)
                .doOnNext(chunk -> {
                    fullAnswer.append(chunk);
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                })
                .doOnComplete(() -> {
                    GuestConversation freshConversation = guestConvRepo.findById(resolvedConversationId)
                            .orElse(finalConversation);
                    GuestMessage assistantMsg = GuestMessage.builder()
                            .guestConversation(freshConversation)
                            .role("assistant")
                            .content(fullAnswer.toString())
                            .build();
                    guestMsgRepo.save(assistantMsg);
                    guestConvRepo.save(freshConversation);
                    emitter.complete();
                })
                .doOnError(emitter::completeWithError)
                .subscribe();
    }

    public void deleteConversation(String guestId, UUID conversationId) {
        // Change RuntimeException to ResponseStatusException to return 404 instead of 500
        GuestConversation conv = guestConvRepo.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        
        if (!conv.getGuestId().equals(guestId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        
        guestConvRepo.delete(conv);
    }

    // Serialize the attachments list to a JSON string for DB storage
    private String serializeAttachments(List<AttachmentDTO> attachments) {
        if (attachments == null || attachments.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(attachments);
        } catch (Exception e) {
            return null;
        }
    }

    // Deserialize the attachments list back from its stored JSON string
    private List<AttachmentDTO> deserializeAttachments(String json) {
        try {
            if (json == null || json.isBlank()) return List.of();
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, AttachmentDTO.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private MessageDTO.Response toDto(GuestMessage message, List<AgentStepDTO> steps) {
        return MessageDTO.Response.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .attachmentUrl(message.getAttachmentUrl())
                .attachmentType(message.getAttachmentType())
                .attachments(deserializeAttachments(message.getAttachments()))
                .agentSteps(steps)
                .createdAt(message.getCreatedAt())
                .build();
    }
}