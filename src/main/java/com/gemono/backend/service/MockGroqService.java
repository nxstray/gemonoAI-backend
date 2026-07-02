package com.gemono.backend.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Profile("test") // Only load this mock service in the test profile to avoid real API calls during testing
public class MockGroqService implements ChatLlmService {

    @Override
    public String chat(List<Map<String, String>> messages) {
        // Get the last user message from the list of messages
        String lastUserMessage = messages.stream()
                .filter(m -> "user".equals(m.get("role")))
                .map(m -> m.get("content"))
                .reduce((first, second) -> second)
                .orElse("Hello");

        return "[MOCK RESPONSE] Received your message: '" + lastUserMessage + "'. This is a simulated response for testing.";
    }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        return "[MOCK RESPONSE] Simulated complete response for: " + userMessage;
    }
}