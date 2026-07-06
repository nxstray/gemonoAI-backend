package com.gemono.backend.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Represents a single file attached to a message — used for the multi-file attachment list
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDTO {
    private String url;
    private String type; // "image", "audio", "pdf", "file"
    private String name;
}