package com.gemono.backend.service;

import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

// Stores files in Supabase Storage via its S3-compatible API — used in production
// since HF Spaces' container filesystem is ephemeral.
@Service
@Profile("prod")
public class SupabaseFileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final String bucket;
    private final String publicUrlBase;

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
            @Value("${supabase.storage.endpoint}") String endpoint,
            @Value("${supabase.storage.region}") String region,
            @Value("${supabase.storage.access-key}") String accessKey,
            @Value("${supabase.storage.secret-key}") String secretKey,
            @Value("${supabase.storage.bucket}") String bucket,
            @Value("${supabase.storage.public-url-base}") String publicUrlBase) {

        this.bucket = bucket;
        this.publicUrlBase = publicUrlBase;

        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true) // required for S3-compatible providers like Supabase
                .build();
    }

    @Override
    public String save(MultipartFile file) throws IOException {
        String extension = FilenameUtils.getExtension(file.getOriginalFilename());
        String key = UUID.randomUUID() + "." + extension;

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(file.getContentType())
                        .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
        );

        // Bucket must be set to "public" in the Supabase dashboard for this URL to be reachable
        return publicUrlBase + "/" + key;
    }

    @Override
    public String toDataUri(String attachmentUrl) throws IOException {
        String key = extractKey(attachmentUrl);
        byte[] bytes = downloadBytes(key);
        String mimeType = EXTENSION_MIME_FALLBACK.getOrDefault(
                FilenameUtils.getExtension(key).toLowerCase(), "application/octet-stream");
        String base64 = Base64.getEncoder().encodeToString(bytes);
        return "data:" + mimeType + ";base64," + base64;
    }

    @Override
    public byte[] readBytes(String attachmentUrl) throws IOException {
        return downloadBytes(extractKey(attachmentUrl));
    }

    private byte[] downloadBytes(String key) throws IOException {
        try (var stream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build())) {
            return stream.readAllBytes();
        }
    }

    private String extractKey(String attachmentUrl) {
        return attachmentUrl.substring(attachmentUrl.lastIndexOf('/') + 1);
    }
}