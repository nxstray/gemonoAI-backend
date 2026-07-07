package com.gemono.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gemono.backend.data.AgentStepDTO;
import com.gemono.backend.data.AttachmentDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

// Core agentic loop: LLM decides which tool to use, executes it, then responds
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final ChatLlmService chatLlmService;
    private final TavilyService tavilyService;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final FileService fileService;
    private final TranscriptionService transcriptionService;
    private final DocumentExtractionService documentExtractionService;

    // Cache TTL — jawaban sama disimpan 1 jam di Redis
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final String CACHE_PREFIX = "agent:cache:";

    private static final List<String> SUPPORTED_IMAGE_MIME_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    // System prompt that instructs the LLM to behave as an agent
    private static final String SYSTEM_PROMPT = """
            You are Gemono, an intelligent AI research assistant.
            
            You have access to the following tool:
            - search(query): Search the web for current information
            
            When answering a user's question:
            1. Decide if you need to search for information first
            2. If yes, respond with ONLY the following text and NOTHING else — no heading,
            no preamble, no explanation before or after: SEARCH: <your search query>
            3. After receiving search results, analyze them and provide a helpful, detailed answer
            4. If you don't need to search, answer directly
            
            Always be helpful, concise, and accurate. Format responses with markdown where appropriate.
            """;

    // Run the agentic loop — check cache first before hitting Groq.
    // Now takes a list of attachments instead of a single attachmentUrl/attachmentType pair,
    // so a message can carry any mix of images, audio, and documents at once.
    public AgentResult run(String userMessage, List<Map<String, String>> history, List<AttachmentDTO> attachments) {

        List<AttachmentDTO> safeAttachments = attachments != null ? attachments : List.of();
        boolean hasAttachment = !safeAttachments.isEmpty();

        // Split attachments by type so each one is routed to the right processing path
        List<AttachmentDTO> images = safeAttachments.stream()
                .filter(a -> "image".equals(a.getType())).collect(Collectors.toList());
        List<AttachmentDTO> audios = safeAttachments.stream()
                .filter(a -> "audio".equals(a.getType())).collect(Collectors.toList());
        List<AttachmentDTO> documents = safeAttachments.stream()
                .filter(a -> "pdf".equals(a.getType()) || "file".equals(a.getType())).collect(Collectors.toList());

        List<AgentStepDTO> preSteps = new ArrayList<>();
        String effectiveMessage = userMessage;

        // Audio attachments: transcribe each one, then treat the transcript as normal text
        for (AttachmentDTO audio : audios) {
            preSteps.add(AgentStepDTO.builder()
                    .type("thinking")
                    .description("Mentranskripsi audio " + audio.getName() + "...")
                    .build());
            try {
                String transcript = transcriptionService.transcribe(audio.getUrl());
                effectiveMessage = appendContext(effectiveMessage,
                        "[Transkripsi audio: " + audio.getName() + "]: " + transcript);
            } catch (Exception e) {
                log.error("Transcription failed for {}: {}", audio.getName(), e.getMessage());
                return new AgentResult(
                        "Maaf, saya gagal memproses audio \"" + audio.getName() + "\". Coba lagi atau kirim dalam bentuk teks.",
                        List.of(AgentStepDTO.builder().type("answer").description("Transkripsi gagal").result("Error").build())
                );
            }
        }

        // Document attachments (PDF/Word/etc.): extract text via Apache Tika, then feed it as context
        for (AttachmentDTO doc : documents) {
            preSteps.add(AgentStepDTO.builder()
                    .type("thinking")
                    .description("Membaca dokumen " + doc.getName() + "...")
                    .build());
            try {
                String extracted = documentExtractionService.extractText(doc.getUrl());
                effectiveMessage = appendContext(effectiveMessage,
                        "[Isi dokumen: " + doc.getName() + "]:\n" + extracted);
            } catch (Exception e) {
                log.error("Document extraction failed for {}: {}", doc.getName(), e.getMessage());
                effectiveMessage = appendContext(effectiveMessage,
                        "[Catatan: dokumen \"" + doc.getName() + "\" tidak bisa dibaca isinya]");
            }
        }

        // Image attachments: route straight to the vision model, bypassing the search/cache flow
        if (!images.isEmpty()) {
            return runWithImages(effectiveMessage, history, images, preSteps);
        }

        // Try cache first — skip Groq if same question already answered
        // (skipped entirely when there's an attachment, since the content differs each time even if the text looks similar)
        if (!hasAttachment) {
            String cacheKey = buildCacheKey(effectiveMessage);
            String cached = getFromCache(cacheKey);
            if (cached != null) {
                log.info("Cache hit for message hash — skipping Groq call");
                List<AgentStepDTO> steps = List.of(
                        AgentStepDTO.builder().type("thinking").description("Retrieving cached answer...").build(),
                        AgentStepDTO.builder().type("answer").description("Responding from cache").result("Done").build()
                );
                return new AgentResult(cached, steps);
            }
        }

        List<AgentStepDTO> steps = new ArrayList<>(preSteps);

        // Build message history for context
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.addAll(history);
        messages.add(Map.of("role", "user", "content", effectiveMessage));

        // Step 1: Ask LLM what to do
        String firstResponse = chatLlmService.chat(messages);
        steps.add(AgentStepDTO.builder()
                .type("thinking")
                .description("Analyzing your request...")
                .result(null)
                .build());

        // Trim leading/trailing whitespace before checking — models sometimes add stray
        // newlines/spaces even when following the SEARCH: instruction correctly
        String trimmedResponse = firstResponse.trim();
        // Step 2: If LLM wants to search, do it
        if (trimmedResponse.startsWith("SEARCH:")) {
            String query = trimmedResponse.substring(7).trim();

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
            messages.add(Map.of("role", "assistant", "content", trimmedResponse));
            messages.add(Map.of("role", "user", "content",
                    "Search results:\n\n" + searchResults + "\n\nNow please provide your final answer."));

            String finalAnswer = chatLlmService.chat(messages);

            steps.add(AgentStepDTO.builder()
                    .type("answer")
                    .description("Generating response")
                    .result("Done")
                    .build());

            // Cache the final answer
            if (!hasAttachment) saveToCache(buildCacheKey(effectiveMessage), finalAnswer);
            return new AgentResult(finalAnswer, steps);
        }

        // No search needed — direct answer
        steps.add(AgentStepDTO.builder()
                .type("answer")
                .description("Responding directly")
                .result("Done")
                .build());

        // Cache direct answer too
        if (!hasAttachment) saveToCache(buildCacheKey(effectiveMessage), trimmedResponse);
        return new AgentResult(trimmedResponse, steps);
    }

    // Handle one or more image attachments via the vision-capable model, separate from the text/search flow
    private AgentResult runWithImages(String userMessage, List<Map<String, String>> history,
                                       List<AttachmentDTO> images, List<AgentStepDTO> preSteps) {
        List<AgentStepDTO> steps = new ArrayList<>(preSteps);
        steps.add(AgentStepDTO.builder()
                .type("thinking")
                .description("Menganalisis gambar...")
                .build());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.addAll(history);

        String prompt = (userMessage == null || userMessage.isBlank())
                ? "Tolong jelaskan gambar ini."
                : userMessage;

        try {
            List<String> dataUris = new ArrayList<>();
            for (AttachmentDTO image : images) {
                String dataUri = fileService.toDataUri(image.getUrl());
                // Reject formats vision models don't support (e.g. .ico) before calling Groq,
                // instead of letting Groq return a confusing "invalid image data" error
                String mimeType = dataUri.substring(5, dataUri.indexOf(';'));
                if (!SUPPORTED_IMAGE_MIME_TYPES.contains(mimeType)) {
                    steps.add(AgentStepDTO.builder().type("answer").description("Format gambar tidak didukung").result("Error").build());
                    return new AgentResult(
                            "Maaf, format gambar \"" + image.getName() + "\" (" + mimeType + ") belum didukung. " +
                            "Coba gunakan format JPG, PNG, WEBP, atau GIF ya.",
                            steps
                    );
                }
                dataUris.add(dataUri);
            }
            String answer = chatLlmService.chatWithImage(messages, prompt, dataUris);
            steps.add(AgentStepDTO.builder().type("answer").description("Menganalisis gambar").result("Done").build());
            return new AgentResult(answer, steps);
        } catch (GroqService.RateLimitException e) {
            throw e;
        } catch (Exception e) {
            log.error("Vision request failed", e); // include full exception with stack trace, not just message
            steps.add(AgentStepDTO.builder().type("answer").description("Gagal memproses gambar").result("Error").build());
            return new AgentResult("Maaf, terjadi kesalahan saat memproses gambar. Coba lagi ya.", steps);
        }
    }

    // Append an extra context block (transcript, extracted document text, etc.) to the user's message
    private String appendContext(String base, String extra) {
        return (base == null || base.isBlank()) ? extra : base + "\n\n" + extra;
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

    // Streaming variant of run() — currently only streams the plain conversational path.
    // Attachments (image/audio/document) still go through the existing blocking run() logic
    // and are emitted as a single chunk, since vision/transcription/document extraction
    // don't have a natural token-by-token streaming shape in this codebase yet.
    public Flux<String> runStream(String userMessage, List<Map<String, String>> history, List<AttachmentDTO> attachments) {

        List<AttachmentDTO> safeAttachments = attachments != null ? attachments : List.of();
        if (!safeAttachments.isEmpty()) {
            AgentResult result = run(userMessage, history, safeAttachments);
            return Flux.just(result.answer());
        }

        String cacheKey = buildCacheKey(userMessage);
        String cached = getFromCache(cacheKey);
        if (cached != null) {
            log.info("Cache hit for message hash — skipping Groq call (stream)");
            return Flux.just(cached);
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.addAll(history);
        messages.add(Map.of("role", "user", "content", userMessage));

        return Flux.create(sink -> {
            StringBuilder peekBuffer = new StringBuilder();
            StringBuilder fullAnswer = new StringBuilder();
            boolean[] resolvedAsDirectAnswer = {false};

            chatLlmService.chatStream(messages).subscribe(
                    chunk -> {
                        if (resolvedAsDirectAnswer[0]) {
                            fullAnswer.append(chunk);
                            sink.next(chunk);
                            return;
                        }
                        peekBuffer.append(chunk);
                        if (peekBuffer.length() >= 7 && !peekBuffer.toString().trim().startsWith("SEARCH:")) {
                            resolvedAsDirectAnswer[0] = true;
                            fullAnswer.append(peekBuffer);
                            sink.next(peekBuffer.toString());
                        }
                    },
                    sink::error,
                    () -> {
                        if (resolvedAsDirectAnswer[0]) {
                            saveToCache(cacheKey, fullAnswer.toString());
                            sink.complete();
                            return;
                        }
                        String firstResponse = peekBuffer.toString().trim();
                        if (firstResponse.startsWith("SEARCH:")) {
                            String query = firstResponse.substring(7).trim();

                            // tavilyService.search() blocks internally (.block()), which is
                            // illegal on a Reactor Netty event-loop thread (this callback runs
                            // on one, since it's driven by chatLlmService.chatStream()'s
                            // subscription). Offload it to boundedElastic so the blocking call
                            // runs on a thread that actually allows blocking — otherwise Reactor
                            // silently drops the resulting error and the SSE stream hangs forever.
                            Mono.fromCallable(() -> tavilyService.search(query))
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .subscribe(
                                            searchResults -> {
                                                List<Map<String, String>> followUp = new ArrayList<>(messages);
                                                followUp.add(Map.of("role", "assistant", "content", firstResponse));
                                                followUp.add(Map.of("role", "user", "content",
                                                        "Search results:\n\n" + searchResults + "\n\nNow please provide your final answer."));

                                                StringBuilder finalAnswer = new StringBuilder();
                                                chatLlmService.chatStream(followUp).subscribe(
                                                        finalChunk -> {
                                                            finalAnswer.append(finalChunk);
                                                            sink.next(finalChunk);
                                                        },
                                                        sink::error,
                                                        () -> {
                                                            saveToCache(cacheKey, finalAnswer.toString());
                                                            sink.complete();
                                                        }
                                                );
                                            },
                                            sink::error
                                    );
                        } else {
                            fullAnswer.append(firstResponse);
                            sink.next(firstResponse);
                            saveToCache(cacheKey, fullAnswer.toString());
                            sink.complete();
                        }
                    }
            );
        });
    }
}