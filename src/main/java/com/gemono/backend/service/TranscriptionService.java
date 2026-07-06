package com.gemono.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class TranscriptionService {

    private final FileService fileService;

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.whisper.model}")
    private String whisperModel;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.groq.com/openai/v1")
            .build();

    @SuppressWarnings("unchecked")
    public String transcribe(String attachmentUrl) throws Exception {
        byte[] audioBytes = fileService.readFileBytes(attachmentUrl);
        String filename = attachmentUrl.substring(attachmentUrl.lastIndexOf('/') + 1);

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() { return filename; }
        });
        builder.part("model", whisperModel);

        Map<String, Object> response = webClient.post()
                .uri("/audio/transcriptions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + groqApiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(builder.build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        Object text = response.get("text");
        return text != null ? text.toString() : "";
    }
}