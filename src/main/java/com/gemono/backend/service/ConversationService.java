package com.gemono.backend.service;

import com.gemono.backend.data.*;
import com.gemono.backend.model.*;
import com.gemono.backend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserService userService;
    private final AgentService agentService;
    private final ChatLlmService chatLlmService;
    private final ObjectMapper objectMapper; // used to serialize/deserialize the attachments list

    private static final String TITLE_SYSTEM_PROMPT = """
        Buatkan judul singkat (maksimal 6 kata) dalam Bahasa Indonesia yang MERANGKUM
        topik utama dari pesan pengguna berikut — JANGAN hanya menyalin ulang kalimat
        aslinya. Balas HANYA dengan judulnya saja, tanpa tanda kutip, tanpa titik di
        akhir, tanpa penjelasan tambahan.
        """;

    // Send a message — creates conversation if needed, runs agent, saves reply.
    // Now accepts a list of attachments instead of a single attachmentUrl/attachmentType pair.
    @Transactional
    public MessageDTO.Response sendMessage(String userEmail, UUID conversationId,
                                            String content, List<AttachmentDTO> attachments) {

        User user = userService.findByEmail(userEmail);
        List<AttachmentDTO> safeAttachments = attachments != null ? attachments : List.of();

        // Get or create conversation
        Conversation conversation;
        if (conversationId != null) {
            conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new RuntimeException("Conversation not found"));
        } else {
            conversation = Conversation.builder()
                    .user(user)
                    .title(generateTitle(content))
                    .build();
            conversation = conversationRepository.save(conversation);
        }

        // First attachment is mirrored into the legacy single-attachment columns
        // so already-stored messages and any old code path keep working
        AttachmentDTO firstAttachment = safeAttachments.isEmpty() ? null : safeAttachments.get(0);

        // Save user message
        Message userMessage = Message.builder()
                .conversation(conversation)
                .role("user")
                .content(content)
                .attachmentUrl(firstAttachment != null ? firstAttachment.getUrl() : null)
                .attachmentType(firstAttachment != null ? firstAttachment.getType() : null)
                .attachments(serializeAttachments(safeAttachments))
                .build();
        messageRepository.save(userMessage);

        // Build history for agent context (last 10 messages)
        List<Message> history = messageRepository
        .findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        // history is ordered oldest→newest; take the LAST 10 (most recent), not the first 10 —
        // otherwise long conversations lose all recent context and the model only "remembers"
        // the very start of the chat
        List<Message> recentHistory = history.size() > 10
                ? history.subList(history.size() - 10, history.size())
                : history;
        List<Map<String, String>> historyMaps = recentHistory.stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList());

        // Run agentic loop
        AgentService.AgentResult result = agentService.run(content, historyMaps, safeAttachments);

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

    @Transactional
    public ConversationDTO.Summary renameConversation(String userEmail, UUID conversationId, String newTitle) {
        User user = userService.findByEmail(userEmail);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        if (!conversation.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        conversation.setTitle(newTitle);
        conversation = conversationRepository.save(conversation);

        return ConversationDTO.Summary.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .updatedAt(conversation.getUpdatedAt())
                .build();
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

    // Streaming variant of sendMessage() — the user message is persisted immediately just
    // like the non-streaming path, but the assistant reply is streamed chunk by chunk over
    // SSE and only written to the DB once the whole stream has completed.
    public void sendMessageStream(String userEmail, UUID conversationId, String content,
                                   List<AttachmentDTO> attachments, SseEmitter emitter) {

        User user = userService.findByEmail(userEmail);
        List<AttachmentDTO> safeAttachments = attachments != null ? attachments : List.of();

        Conversation conversation;
        if (conversationId != null) {
            conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new RuntimeException("Conversation not found"));
        } else {
            Conversation newConversation = Conversation.builder()
                    .user(user)
                    .title(generateTitle(content))
                    .build();
            conversation = conversationRepository.save(newConversation);
        }
        // Captured once into a truly final reference so it's safe to use inside the lambdas below
        final Conversation finalConversation = conversation;
        final UUID resolvedConversationId = finalConversation.getId();

        AttachmentDTO firstAttachment = safeAttachments.isEmpty() ? null : safeAttachments.get(0);
        Message userMessage = Message.builder()
                .conversation(finalConversation)
                .role("user")
                .content(content)
                .attachmentUrl(firstAttachment != null ? firstAttachment.getUrl() : null)
                .attachmentType(firstAttachment != null ? firstAttachment.getType() : null)
                .attachments(serializeAttachments(safeAttachments))
                .build();
        messageRepository.save(userMessage);

        List<Message> history = messageRepository.findByConversationIdOrderByCreatedAtAsc(resolvedConversationId);
        List<Message> recentHistory = history.size() > 10
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
                    Conversation freshConversation = conversationRepository.findById(resolvedConversationId)
                            .orElse(finalConversation);
                    Message assistantMessage = Message.builder()
                            .conversation(freshConversation)
                            .role("assistant")
                            .content(fullAnswer.toString())
                            .build();
                    messageRepository.save(assistantMessage);
                    conversationRepository.save(freshConversation);
                    emitter.complete();
                })
                .doOnError(emitter::completeWithError)
                .subscribe();
    }

    private String generateTitle(String content) {
        try {
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", TITLE_SYSTEM_PROMPT),
                    Map.of("role", "user", "content", content)
            );
            String title = chatLlmService.chat(messages).trim();
            title = title.replaceAll("^\"|\"$", ""); // buang kutip kalau LLM ikut nambahin
            if (title.isBlank()) return fallbackTitle(content);
            return title.length() > 60 ? title.substring(0, 60) + "..." : title;
        } catch (Exception e) {
            log.warn("Title generation failed, using fallback: {}", e.getMessage());
            return fallbackTitle(content);
        }
    }

    private String fallbackTitle(String content) {
        return content.length() > 50 ? content.substring(0, 50) + "..." : content;
    }

    // Serialize the attachments list to a JSON string for DB storage — mirrors how agentSteps is stored
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

    // Map Message entity to DTO
    private MessageDTO.Response toDto(Message message, List<AgentStepDTO> steps) {
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