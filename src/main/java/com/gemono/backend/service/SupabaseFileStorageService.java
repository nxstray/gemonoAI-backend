package com.gemono.backend.service;

import org.apache.commons.io.FilenameUtils;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

// Stores files in Supabase Storage (public bucket) via its REST API — used in production
// since HF Spaces' container filesystem is ephemeral and would lose all uploads on restart.
@Service
@Profile("prod")
public class SupabaseFileStorageService implements FileStorageService {

    private final WebClient webClient;
    private final String bucket;
    private final String publicUrlBase;
    private final String serviceKey;

    private final Tika tika = new Tika();

    private static final Map<String, String> EXTENSION_MIME_FALLBACK = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp",
            "gif", "image/gif",
            "mp3", "audio/mpeg",
            "wav", "audio/wav",
            "webm", "audio/webm",
            "pdf", "application/pdf"
    );

    public SupabaseFileStorageService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.service-key}") String serviceKey,
            @Value("${supabase.bucket}") String bucket) {
        this.serviceKey = serviceKey;
        this.bucket = bucket;
        this.publicUrlBase = supabaseUrl + "/storage/v1/object/public/" + bucket;
        this.webClient = WebClient.builder()
                .baseUrl(supabaseUrl + "/storage/v1")
                .build();
    }

    @Override
    public String save(MultipartFile file) throws IOException {
        String extension = FilenameUtils.getExtension(file.getOriginalFilename());
        String path = UUID.randomUUID() + "." + extension;
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        webClient.post()
                .uri("/object/{bucket}/{path}", bucket, path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceKey)
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .bodyValue(file.getBytes())
                .retrieve()
                .toBodilessEntity()
                .block();

        return publicUrlBase + "/" + path;
    }

    @Override
    public byte[] readBytes(String attachmentUrl) throws IOException {
        String path = extractPath(attachmentUrl);
        return webClient.get()
                .uri("/object/{bucket}/{path}", bucket, path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceKey)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
    }

    @Override
    public String toDataUri(String attachmentUrl) throws IOException {
        byte[] bytes = readBytes(attachmentUrl);

        String mimeType = tika.detect(bytes);
        if (mimeType == null || mimeType.equals("application/octet-stream")) {
            String ext = FilenameUtils.getExtension(attachmentUrl).toLowerCase();
            mimeType = EXTENSION_MIME_FALLBACK.getOrDefault(ext, "application/octet-stream");
        }

        if (bytes.length > 3 * 1024 * 1024) {
            throw new IOException("Image file too large for vision processing (max ~3MB)");
        }

        String base64 = Base64.getEncoder().encodeToString(bytes);
        return "data:" + mimeType + ";base64," + base64;
    }

    private String extractPath(String attachmentUrl) {
        return attachmentUrl.substring(attachmentUrl.lastIndexOf('/') + 1);
    }
}