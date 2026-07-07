package com.gemono.backend.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

// Abstracts where uploaded files physically live — local disk for development,
// Supabase Storage (S3-compatible) for production.
public interface FileStorageService {
    String save(MultipartFile file) throws IOException;
    byte[] readBytes(String url) throws IOException;
    String toDataUri(String url) throws IOException;
}