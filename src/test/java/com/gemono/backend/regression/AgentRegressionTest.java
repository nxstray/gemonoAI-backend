package com.gemono.backend.regression;

import com.gemono.backend.data.AgentStepDTO;
import com.gemono.backend.service.AgentService;
import com.gemono.backend.service.GroqService;
import com.gemono.backend.service.TavilyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Agent Logic Regression Tests — ensure agentic loop behavior has
 * not changed after refactoring AgentService or GroqService.
 *
 * Run: mvn test -Dgroups=regression
 */
@Epic("Regression")
@Feature("Agent Logic")
@Tag("regression")
@ExtendWith(MockitoExtension.class)
@DisplayName("Regression — Agent API Endpoints")
class AgentRegressionTest {

    @Mock private GroqService groqService;
    @Mock private TavilyService tavilyService;
    @InjectMocks private AgentService agentService;

    @BeforeEach
    void injectObjectMapper() throws Exception {
        var field = AgentService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(agentService, new ObjectMapper());
    }

    // TC-REG-16
    @Test
    @Story("Agent Loop")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-REG-16 | Agent always returns non-null, non-blank answer")
    void TC_REG_16_agentAlwaysReturnsAnswer() {
        when(groqService.chat(anyList())).thenReturn("The answer is 42.");

        AgentService.AgentResult result = agentService.run("What is the answer?", List.of(), List.of());

        assertThat(result).isNotNull();
        assertThat(result.answer()).isNotNull().isNotBlank();
    }

    // TC-REG-17
    @Test
    @Story("Agent Loop")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-REG-17 | Agent always returns non-empty steps list")
    void TC_REG_17_agentAlwaysReturnsSteps() {
        when(groqService.chat(anyList())).thenReturn("Direct answer here.");

        AgentService.AgentResult result = agentService.run("Question", List.of(), List.of());

        assertThat(result.steps()).isNotNull().isNotEmpty();
    }

    // TC-REG-18
    @Test
    @Story("Agent Loop")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-REG-18 | SEARCH prefix detection is case-sensitive — must be uppercase")
    void TC_REG_18_searchPrefixCaseSensitive() {
        // Lowercase "search:" should NOT trigger tool — treat as direct answer
        when(groqService.chat(anyList())).thenReturn("search: this should not trigger tool");

        AgentService.AgentResult result = agentService.run("Test", List.of(), List.of());

        // TavilyService must NOT be called
        verify(tavilyService, never()).search(any());
        assertThat(result.answer()).contains("search:");
    }

    // TC-REG-19
    @Test
    @Story("Agent Loop")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-REG-19 | Agent handles empty history without error")
    void TC_REG_19_agentHandlesEmptyHistory() {
        when(groqService.chat(anyList())).thenReturn("No history, still works.");

        assertThatCode(() -> agentService.run("Question", List.of(), List.of()))
                .doesNotThrowAnyException();
    }

    // TC-REG-20
    @Test
    @Story("Agent Loop")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-REG-20 | Agent step types are exactly: thinking, search, answer — no others")
    void TC_REG_20_agentStepTypesAreKnown() {
        when(groqService.chat(anyList()))
                .thenReturn("SEARCH: test query")
                .thenReturn("Final answer");
        when(tavilyService.search(anyString())).thenReturn("Results");

        AgentService.AgentResult result = agentService.run("Question", List.of(), List.of());

        List<String> knownTypes = List.of("thinking", "search", "answer");
        result.steps().forEach(step ->
                assertThat(knownTypes).contains(step.getType())
        );
    }

    // TC-REG-21
    @Test
    @Story("Serialization")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-REG-21 | Step serialization produces valid JSON — not null or empty string")
    void TC_REG_21_stepSerializationProducesValidJson() {
        List<AgentStepDTO> steps = List.of(
                AgentStepDTO.builder().type("thinking").description("Analyzing").build(),
                AgentStepDTO.builder().type("answer").description("Responding").result("Done").build()
        );

        String json = agentService.serializeSteps(steps);

        assertThat(json).isNotNull().isNotBlank();
        assertThat(json).startsWith("[").endsWith("]");
        assertThat(json).contains("thinking").contains("answer");
    }
}