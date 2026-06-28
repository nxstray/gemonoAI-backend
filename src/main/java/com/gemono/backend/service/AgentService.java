package com.gemono.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gemono.backend.data.AgentStepDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

// Core agentic loop: LLM decides which tool to use, executes it, then responds
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final GroqService groqService;
    private final TavilyService tavilyService;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    // Cache TTL — jawaban sama disimpan 1 jam di Redis
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final String CACHE_PREFIX = "agent:cache:";

    // System prompt that instructs the LLM to behave as an agent
    private static final String SYSTEM_PROMPT = """
            You are Gemono, an intelligent AI research assistant.
            
            You have access to the following tool:
            - search(query): Search the web for current information
            
            When answering a user's question:
            1. Decide if you need to search for information first
            2. If yes, respond ONLY with: SEARCH: <your search query>
            3. After receiving search results, analyze them and provide a helpful, detailed answer
            4. If you don't need to search, answer directly
            
            Always be helpful, concise, and accurate. Format responses with markdown where appropriate.
            """;

    // Run the agentic loop — check cache first before hitting Groq
    public AgentResult run(String userMessage, List<Map<String, String>> history) {
        // Try cache first — skip Groq if same question already answered
        String cacheKey = buildCacheKey(userMessage);
        String cached = getFromCache(cacheKey);

        if (cached != null) {
            log.info("Cache hit for message hash — skipping Groq call");
            List<AgentStepDTO> steps = List.of(
                AgentStepDTO.builder().type("thinking").description("Retrieving cached answer...").build(),
                AgentStepDTO.builder().type("answer").description("Responding from cache").result("Done").build()
            );
            return new AgentResult(cached, steps);
        }

        List<AgentStepDTO> steps = new ArrayList<>();

        // Build message history for context
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.addAll(history);
        messages.add(Map.of("role", "user", "content", userMessage));

        // Step 1: Ask LLM what to do
        String firstResponse = groqService.chat(messages);
        steps.add(AgentStepDTO.builder()
                .type("thinking")
                .description("Analyzing your request...")
                .result(null)
                .build());

        // Step 2: If LLM wants to search, do it
        if (firstResponse.startsWith("SEARCH:")) {
            String query = firstResponse.substring(7).trim();

            steps.add(AgentStepDTO.builder()
                    .type("search")
                    .description("Searching: " + query)
                    .result(null)
                    .build());

            String searchResults = tavilyService.search(query);

            steps.add(AgentStepDTO.builder()
                    .type("search")
                    .description("Searching: " + query)
                    .result("Found relevant information")
                    .build());

            // Step 3: Feed results back to LLM for final answer
            messages.add(Map.of("role", "assistant", "content", firstResponse));
            messages.add(Map.of("role", "user", "content",
                    "Search results:\n\n" + searchResults + "\n\nNow please provide your final answer."));

            String finalAnswer = groqService.chat(messages);

            steps.add(AgentStepDTO.builder()
                    .type("answer")
                    .description("Generating response")
                    .result("Done")
                    .build());

            // Cache the final answer
            saveToCache(cacheKey, finalAnswer);
            return new AgentResult(finalAnswer, steps);
        }

        // No search needed — direct answer
        steps.add(AgentStepDTO.builder()
                .type("answer")
                .description("Responding directly")
                .result("Done")
                .build());

        // Cache direct answer too
        saveToCache(cacheKey, firstResponse);
        return new AgentResult(firstResponse, steps);
    }

    // Build cache key — MD5 hash dari lowercase trimmed message
    // Sehingga "what is AI" dan "What is AI?" → key sama
    private String buildCacheKey(String message) {
        try {
            String normalized = message.toLowerCase().trim();
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return CACHE_PREFIX + hex;
        } catch (Exception e) {
            // Fallback ke raw message jika MD5 gagal
            return CACHE_PREFIX + message.toLowerCase().trim().hashCode();
        }
    }

    private String getFromCache(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Cache read failed, proceeding without cache: {}", e.getMessage());
            return null;
        }
    }

    private void saveToCache(String key, String value) {
        try {
            redisTemplate.opsForValue().set(key, value, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Cache write failed: {}", e.getMessage());
        }
    }

    // Serialize steps list to JSON string for DB storage
    public String serializeSteps(List<AgentStepDTO> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (Exception e) {
            return "[]";
        }
    }

    // Deserialize steps from DB JSON string
    public List<AgentStepDTO> deserializeSteps(String json) {
        try {
            if (json == null) return List.of();
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, AgentStepDTO.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    // Simple record to hold agent output
    public record AgentResult(String answer, List<AgentStepDTO> steps) {}
}