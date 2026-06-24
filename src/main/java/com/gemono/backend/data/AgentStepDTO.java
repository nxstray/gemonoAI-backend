package com.gemono.backend.data;

import lombok.*;

// Represents a single step taken by the AI agent during reasoning
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStepDTO {
    // "thinking", "search", "summarize", "answer"
    private String type;
    private String description;
    private String result;
}