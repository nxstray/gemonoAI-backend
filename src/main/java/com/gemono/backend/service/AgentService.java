package com.gemono.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gemono.backend.data.AgentStepDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

// Core agentic loop: LLM decides which tool to use, executes it, then responds
@Service
@RequiredArgsConstructor
public class AgentService {

    private final GroqService groqService;
    private final TavilyService tavilyService;
    private final ObjectMapper objectMapper;

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

    // Run the agentic loop for a user message, returns steps + final answer
    public AgentResult run(String userMessage, List<Map<String, String>> history) {
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

            return new AgentResult(finalAnswer, steps);
        }

        // No search needed — direct answer
        steps.add(AgentStepDTO.builder()
                .type("answer")
                .description("Responding directly")
                .result("Done")
                .build());

        return new AgentResult(firstResponse, steps);
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