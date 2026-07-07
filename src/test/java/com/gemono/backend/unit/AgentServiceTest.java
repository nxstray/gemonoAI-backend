package com.gemono.backend.unit;

import com.gemono.backend.data.AgentStepDTO;
import com.gemono.backend.service.AgentService;
import com.gemono.backend.service.GroqService;
import com.gemono.backend.service.TavilyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Epic("Agent")
@Feature("AgentService")
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test — AgentService")
class AgentServiceTest {

    @Mock private GroqService groqService;
    @Mock private TavilyService tavilyService;
    @InjectMocks private AgentService agentService;

    @BeforeEach
    void injectObjectMapper() throws Exception {
        var field = AgentService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(agentService, new ObjectMapper());
    }

    @Test
    @Story("Direct Answer")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-UNIT-01 | Agent returns direct answer when no search needed")
    void TC_UNIT_01_directAnswerWhenNoSearchNeeded() {
        when(groqService.chat(anyList())).thenReturn("Paris is the capital of France.");

        AgentService.AgentResult result = agentService.run("Capital of France?", List.of(), List.of());

        assertThat(result.answer()).isEqualTo("Paris is the capital of France.");
        assertThat(result.steps()).isNotEmpty();
        verify(tavilyService, never()).search(any());
    }

    @Test
    @Story("Search Tool")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-UNIT-02 | Agent triggers search when LLM requests SEARCH prefix")
    void TC_UNIT_02_triggersSearchWhenLLMRequests() {
        when(groqService.chat(anyList()))
            .thenReturn("SEARCH: latest AI breakthroughs 2025")
            .thenReturn("Here is a summary of recent AI news.");
        when(tavilyService.search("latest AI breakthroughs 2025")).thenReturn("AI results content");

        AgentService.AgentResult result = agentService.run("Latest AI news?", List.of(), List.of());

        assertThat(result.answer()).contains("summary");
        assertThat(result.steps()).anyMatch(s -> "search".equals(s.getType()));
        verify(tavilyService).search("latest AI breakthroughs 2025");
    }

    @Test
    @Story("Search Tool")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-UNIT-03 | Agent steps include thinking, search, and answer phases")
    void TC_UNIT_03_agentStepsIncludeAllPhases() {
        when(groqService.chat(anyList()))
            .thenReturn("SEARCH: test query")
            .thenReturn("Final answer");
        when(tavilyService.search(anyString())).thenReturn("search results");

        AgentService.AgentResult result = agentService.run("Some query", List.of(), List.of());

        boolean hasThinking = result.steps().stream().anyMatch(s -> "thinking".equals(s.getType()));
        boolean hasSearch   = result.steps().stream().anyMatch(s -> "search".equals(s.getType()));
        boolean hasAnswer   = result.steps().stream().anyMatch(s -> "answer".equals(s.getType()));

        assertThat(hasThinking).isTrue();
        assertThat(hasSearch).isTrue();
        assertThat(hasAnswer).isTrue();
    }

    @Test
    @Story("Serialization")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-UNIT-04 | Agent steps serialize and deserialize correctly (round-trip)")
    void TC_UNIT_04_stepsSerializeRoundTrip() {
        List<AgentStepDTO> steps = List.of(
            AgentStepDTO.builder().type("search").description("Searching for topic").result("Found 5 results").build(),
            AgentStepDTO.builder().type("answer").description("Generating response").result("Done").build()
        );

        String json = agentService.serializeSteps(steps);
        List<AgentStepDTO> back = agentService.deserializeSteps(json);

        assertThat(back).hasSize(2);
        assertThat(back.get(0).getType()).isEqualTo("search");
        assertThat(back.get(1).getType()).isEqualTo("answer");
    }

    @Test
    @Story("Serialization")
    @Severity(SeverityLevel.MINOR)
    @DisplayName("TC-UNIT-05 | Deserializing null or empty JSON returns empty list")
    void TC_UNIT_05_deserializeNullReturnsEmpty() {
        assertThat(agentService.deserializeSteps(null)).isEmpty();
        assertThat(agentService.deserializeSteps("")).isEmpty();
    }

    @Test
    @Story("History")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-UNIT-06 | Agent includes conversation history in LLM context")
    void TC_UNIT_06_agentIncludesHistoryInContext() {
        when(groqService.chat(anyList())).thenReturn("Based on our conversation, the answer is 42.");

        var history = List.of(
            java.util.Map.of("role", "user", "content", "Previous question"),
            java.util.Map.of("role", "assistant", "content", "Previous answer")
        );

        AgentService.AgentResult result = agentService.run("Follow-up question", history, List.of());

        // Verify groq was called with a messages list containing history
        verify(groqService).chat(argThat(msgs -> msgs.size() >= 4)); // system + 2 history + user
        assertThat(result.answer()).isNotBlank();   
    }
}