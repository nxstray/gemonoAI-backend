package com.gemono.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

// Thin coordinator — delegates actual storage work to whichever FileStorageService
// implementation is active for the current profile (Local for dev, Supabase for prod).
// Callers (AgentService, TranscriptionService, DocumentExtractionService, controllers)
// don't need to change: this class's public API stays exactly the same.
@Service
@RequiredArgsConstructor
public class FileService {

    private final FileStorageService fileStorageService;

    public String saveFile(MultipartFile file) throws IOException {
        return fileStorageService.save(file);
    }

    public String getFileType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) return "file";
        if (contentType.startsWith("image/")) return "image";
        if (contentType.startsWith("audio/")) return "audio";
        if (contentType.equals("application/pdf")) return "pdf";
        return "file";
    }

    public String toDataUri(String attachmentUrl) throws IOException {
        return fileStorageService.toDataUri(attachmentUrl);
    }

    public byte[] readFileBytes(String attachmentUrl) throws IOException {
        return fileStorageService.readBytes(attachmentUrl);
    }
}