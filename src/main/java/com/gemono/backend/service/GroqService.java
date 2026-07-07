package com.gemono.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile({"!test", "prod-test"})
public class GroqService implements ChatLlmService {

    private final WebClient webClient;
    private final String groqApiKey;
    private final String model;
    private final String visionModel;
    private final ObjectMapper objectMapper; // used to parse each streamed SSE chunk from Groq

    public GroqService(
            @Value("${groq.api.url}") String groqApiUrl,
            @Value("${groq.api.key}") String groqApiKey,
            @Value("${groq.model}") String model,
            @Value("${groq.vision.model}") String visionModel,
            ObjectMapper objectMapper) {
        this.groqApiKey = groqApiKey;
        this.model = model;
        this.visionModel = visionModel;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(groqApiUrl)
                .build();
    }

    @Override
    public String chat(List<Map<String, String>> messages) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.7);
        body.put("max_tokens", 2048);
        return executeRequest(body);
    }

    @Override
    public String chatWithImage(List<Map<String, String>> history, String userText, List<String> imageDataUris) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Map<String, String> h : history) {
            messages.add(Map.of("role", h.get("role"), "content", h.get("content")));
        }

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", userText));
        for (String dataUri : imageDataUris) {
            content.add(Map.of("type", "image_url", "image_url", Map.of("url", dataUri)));
        }
        messages.add(Map.of("role", "user", "content", content));

        Map<String, Object> body = new HashMap<>();
        body.put("model", visionModel);
        body.put("messages", messages);
        body.put("temperature", 0.7);
        body.put("max_tokens", 2048);
        // qwen/qwen3.6-27b is a reasoning model — without this, its internal "thinking"
        // text (e.g. "Plan:", "I need to describe...") leaks into the visible answer
        body.put("reasoning_format", "hidden");
        return executeRequest(body);
    }

    // Streams the assistant reply chunk by chunk using Groq's native SSE support ("stream": true).
    // Each SSE event's data is a JSON chunk shaped like:
    // {"choices":[{"delta":{"content":"..."}}]} — we parse it and emit ONLY the delta text,
    // not the raw JSON envelope (id/model/system_fingerprint/etc).
    @Override
    public Flux<String> chatStream(List<Map<String, String>> messages) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.7);
        body.put("max_tokens", 2048);
        body.put("stream", true);

        return webClient.post()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + groqApiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .mapNotNull(ServerSentEvent::data)
                .takeWhile(data -> data != null && !"[DONE]".equals(data))
                .mapNotNull(this::extractDeltaContent);
    }

    // Pull the incremental text out of one Groq streaming chunk's JSON payload
    @SuppressWarnings("unchecked")
    private String extractDeltaContent(String jsonChunk) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(jsonChunk, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) parsed.get("choices");
            if (choices == null || choices.isEmpty()) return null;
            Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
            Object contentPart = delta != null ? delta.get("content") : null;
            return contentPart != null ? contentPart.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String executeRequest(Map<String, Object> body) {
        Map<String, Object> response = webClient.post()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + groqApiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .map(errorBody -> {
                                    System.err.println("Groq 4xx error: " + errorBody);
                                    if (errorBody.contains("rate_limit_exceeded")
                                            || errorBody.contains("Rate limit reached")) {
                                        return new RateLimitException("AI rate limit reached. Please wait a moment and try again.");
                                    }
                                    return new RuntimeException("Groq API error: " + errorBody);
                                })
                )
                .bodyToMono(Map.class)
                .block();

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        Map<String, Object> firstChoice = choices.get(0);
        Map<String, String> message = (Map<String, String>) firstChoice.get("message");
        return message.get("content");
    }

    public String complete(String systemPrompt, String userMessage) {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
        );
        return chat(messages);
    }

    public static class RateLimitException extends RuntimeException {
        public RateLimitException(String message) {
            super(message);
        }
    }
}