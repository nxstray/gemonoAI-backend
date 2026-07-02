package com.gemono.backend.service;

import java.util.List;
import java.util.Map;

public interface ChatLlmService {
    String chat(List<Map<String, String>> messages);
    String complete(String systemPrompt, String userMessage);
}