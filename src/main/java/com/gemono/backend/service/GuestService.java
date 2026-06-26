package com.gemono.backend.service;

import com.gemono.backend.data.MessageDTO;
import com.gemono.backend.data.*;
import com.gemono.backend.model.*;
import com.gemono.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // Guest sends a message — creates conversation if needed, runs agent
    @Transactional
    public MessageDTO.Response sendMessage(String guestId, UUID conversationId,
                                            String content, String attachmentUrl, String attachmentType) {

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

        // Save user message
        GuestMessage userMsg = GuestMessage.builder()
                .guestConversation(conversation)
                .role("user")
                .content(content)
                .attachmentUrl(attachmentUrl)
                .attachmentType(attachmentType)
                .build();
        guestMsgRepo.save(userMsg);

        // Build history for agent context
        List<GuestMessage> history = guestMsgRepo
                .findByGuestConversationIdOrderByCreatedAtAsc(conversation.getId());
        List<Map<String, String>> historyMaps = history.stream()
                .limit(10)
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList());

        // Run agent
        AgentService.AgentResult result = agentService.run(content, historyMaps);

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

    public void deleteConversation(String guestId, UUID conversationId) {
        GuestConversation conv = guestConvRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Not found"));
        if (!conv.getGuestId().equals(guestId)) throw new RuntimeException("Access denied");
        guestConvRepo.delete(conv);
    }

    private MessageDTO.Response toDto(GuestMessage message, List<AgentStepDTO> steps) {
        return MessageDTO.Response.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .attachmentUrl(message.getAttachmentUrl())
                .attachmentType(message.getAttachmentType())
                .agentSteps(steps)
                .createdAt(message.getCreatedAt())
                .build();
    }
}