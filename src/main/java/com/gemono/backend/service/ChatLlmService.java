package com.gemono.backend.service;

import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface ChatLlmService {
    String chat(List<Map<String, String>> messages);
    String complete(String systemPrompt, String userMessage);

    // Vision: kirim satu atau lebih gambar (base64 data URI) + teks ke model.
    // Default: implementasi yang tidak override ini (mis. mock di test) fallback
    // ke jawaban teks biasa tanpa memproses gambar sama sekali.
    default String chatWithImage(List<Map<String, String>> history, String userText, List<String> imageDataUris) {
        List<Map<String, String>> messages = new ArrayList<>(history);
        messages.add(Map.of("role", "user", "content", userText));
        return chat(messages);
    }

    // Streaming variant of chat() — emits the answer as it's generated, chunk by chunk.
    // Default: implementations that don't override this (e.g. test mocks) fall back to
    // emitting the full non-streamed answer as a single chunk.
    default Flux<String> chatStream(List<Map<String, String>> messages) {
        return Flux.just(chat(messages));
    }
}