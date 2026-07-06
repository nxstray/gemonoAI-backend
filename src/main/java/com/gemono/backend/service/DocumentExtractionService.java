package com.gemono.backend.service;

import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

// Extracts plain text from uploaded documents (PDF, Word, etc.) using Apache Tika,
// so the extracted text can be fed to the LLM as extra context
@Service
@RequiredArgsConstructor
public class DocumentExtractionService {

    private final FileService fileService;
    private final Tika tika = new Tika();

    // Character cap to avoid blowing up the LLM context window with huge documents
    private static final int MAX_EXTRACTED_CHARS = 12000;

    public String extractText(String attachmentUrl) throws Exception {
        byte[] bytes = fileService.readFileBytes(attachmentUrl);
        String text = tika.parseToString(new ByteArrayInputStream(bytes));
        if (text == null) return "";
        text = text.trim();
        if (text.length() > MAX_EXTRACTED_CHARS) {
            text = text.substring(0, MAX_EXTRACTED_CHARS) + "\n...(dipotong, dokumen terlalu panjang)";
        }
        return text;
    }
}