package com.gemono.backend.service;

import com.gemono.backend.data.*;
import com.gemono.backend.model.*;
import com.gemono.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserService userService;
    private final AgentService agentService;

    // Send a message — creates conversation if needed, runs agent, saves reply
    @Transactional
    public MessageDTO.Response sendMessage(String userEmail, UUID conversationId,
                                            String content, String attachmentUrl, String attachmentType) {

        User user = userService.findByEmail(userEmail);

        // Get or create conversation
        Conversation conversation;
        if (conversationId != null) {
            conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new RuntimeException("Conversation not found"));
        } else {
            // Auto-title based on first message
            String title = content.length() > 50 ? content.substring(0, 50) + "..." : content;
            conversation = Conversation.builder()
                    .user(user)
                    .title(title)
                    .build();
            conversation = conversationRepository.save(conversation);
        }

        // Save user message
        Message userMessage = Message.builder()
                .conversation(conversation)
                .role("user")
                .content(content)
                .attachmentUrl(attachmentUrl)
                .attachmentType(attachmentType)
                .build();
        messageRepository.save(userMessage);

        // Build history for agent context (last 10 messages)
        List<Message> history = messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        List<Map<String, String>> historyMaps = history.stream()
                .limit(10)
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList());

        // Run agentic loop
        AgentService.AgentResult result = agentService.run(content, historyMaps);

        // Save assistant reply
        Message assistantMessage = Message.builder()
                .conversation(conversation)
                .role("assistant")
                .content(result.answer())
                .agentSteps(agentService.serializeSteps(result.steps()))
                .build();
        messageRepository.save(assistantMessage);

        // Touch conversation updatedAt
        conversationRepository.save(conversation);

        return toDto(assistantMessage, result.steps());
    }

    public List<ConversationDTO.Summary> getConversations(String userEmail) {
        User user = userService.findByEmail(userEmail);
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(user.getId())
                .stream()
                .map(c -> ConversationDTO.Summary.builder()
                        .id(c.getId())
                        .title(c.getTitle())
                        .updatedAt(c.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public ConversationDTO.Detail getConversationDetail(String userEmail, UUID conversationId) {
        User user = userService.findByEmail(userEmail);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        if (!conversation.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        List<Message> messages = messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId);

        return ConversationDTO.Detail.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .messages(messages.stream()
                        .map(m -> toDto(m, agentService.deserializeSteps(m.getAgentSteps())))
                        .collect(Collectors.toList()))
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    public void deleteConversation(String userEmail, UUID conversationId) {
        User user = userService.findByEmail(userEmail);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        if (!conversation.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        conversationRepository.delete(conversation);
    }

    // Map Message entity to DTO
    private MessageDTO.Response toDto(Message message, List<AgentStepDTO> steps) {
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