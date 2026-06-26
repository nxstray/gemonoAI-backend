package com.gemono.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

// Handles communication with Groq LLM API
@Service
public class GroqService {

    private final WebClient webClient;
    private final String groqApiKey;
    private final String model;

    public GroqService(
            @Value("${groq.api.url}") String groqApiUrl,
            @Value("${groq.api.key}") String groqApiKey,
            @Value("${groq.model}") String model) {
        this.groqApiKey = groqApiKey;
        this.model = model;
        // Build WebClient with baseUrl so @Value properties are fully resolved first
        this.webClient = WebClient.builder()
                .baseUrl(groqApiUrl)
                .build();
    }

    // Send a list of messages to Groq and return the assistant reply
    @SuppressWarnings("unchecked")
    public String chat(List<Map<String, String>> messages) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "temperature", 0.7,
                "max_tokens", 2048
        );

        Map<String, Object> response = webClient.post()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + groqApiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                // Log 4xx errors from Groq for easier debugging
                .onStatus(status -> status.is4xxClientError(), clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .map(errorBody -> {
                                    System.err.println("Groq 4xx error: " + errorBody);
                                    return new RuntimeException("Groq API error: " + errorBody);
                                })
                )
                .bodyToMono(Map.class)
                .block();

        // Extract content from choices[0].message.content
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        Map<String, Object> firstChoice = choices.get(0);
        Map<String, String> message = (Map<String, String>) firstChoice.get("message");
        return message.get("content");
    }

    // Single-turn completion helper
    public String complete(String systemPrompt, String userMessage) {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
        );
        return chat(messages);
    }
}