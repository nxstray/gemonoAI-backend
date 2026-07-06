package com.gemono.backend.service;

import org.apache.commons.io.FilenameUtils;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

// Stores files on local disk — used for local development only.
// NOT suitable for HF Spaces production: the container filesystem is ephemeral
// and gets wiped on every restart/redeploy. See SupabaseFileStorageService for prod.
@Service
@Profile("!prod")
public class LocalFileStorageService implements FileStorageService {

    @Value("${file.upload.dir}")
    private String uploadDir;

    // Used to reliably detect a file's real MIME type by sniffing its magic bytes —
    // far more accurate across OSes than Files.probeContentType(), which is known to
    // return null or the wrong type inconsistently depending on the platform
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

    @Override
    public String save(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String extension = FilenameUtils.getExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID() + "." + extension;
        Path filePath = uploadPath.resolve(filename);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/" + filename;
    }

    @Override
    public String toDataUri(String attachmentUrl) throws IOException {
        Path filePath = resolvePath(attachmentUrl);
        byte[] bytes = Files.readAllBytes(filePath);

        String mimeType = tika.detect(bytes);
        if (mimeType == null || mimeType.equals("application/octet-stream")) {
            String ext = FilenameUtils.getExtension(filePath.getFileName().toString()).toLowerCase();
            mimeType = EXTENSION_MIME_FALLBACK.getOrDefault(ext, "application/octet-stream");
        }

        // Groq's vision API caps base64-encoded image requests at 4MB total —
        // fail early with a clear message instead of letting Groq reject it as a vague 4xx
        if (bytes.length > 3 * 1024 * 1024) { // ~3MB raw ≈ ~4MB after base64 overhead
            throw new IOException("Image file too large for vision processing (max ~3MB)");
        }

        String base64 = Base64.getEncoder().encodeToString(bytes);
        return "data:" + mimeType + ";base64," + base64;
    }

    @Override
    public byte[] readBytes(String attachmentUrl) throws IOException {
        return Files.readAllBytes(resolvePath(attachmentUrl));
    }

    private Path resolvePath(String attachmentUrl) {
        String filename = attachmentUrl.substring(attachmentUrl.lastIndexOf('/') + 1);
        return Paths.get(uploadDir).resolve(filename);
    }
}