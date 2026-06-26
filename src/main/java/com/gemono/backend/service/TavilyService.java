package com.gemono.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Tavily search API integration — used as agent search tool
@Service
@RequiredArgsConstructor
public class TavilyService {

    @Value("${tavily.api.url}")
    private String tavilyApiUrl;

    @Value("${tavily.api.key}")
    private String tavilyApiKey;

    private final WebClient webClient = WebClient.create();

    // Search the web and return top result snippets as a single string
    @SuppressWarnings("unchecked")
    public String search(String query) {
        Map<String, Object> body = Map.of(
                "api_key", tavilyApiKey,
                "query", query,
                "search_depth", "basic",
                "max_results", 5,
                "include_answer", true
        );

        Map<String, Object> response = webClient.post()
                .uri(tavilyApiUrl)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        // Prefer the auto-generated answer if available
        String answer = (String) response.get("answer");
        if (answer != null && !answer.isBlank()) {
            return answer;
        }

        // Fallback: join top result snippets
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
        return results.stream()
                .limit(3)
                .map(r -> (String) r.get("content"))
                .collect(Collectors.joining("\n\n"));
    }
}