package com.ideapipeline.orchestrator;

import com.ideapipeline.client.LlmClient;
import com.ideapipeline.config.PipelineProperties;
import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.CritiqueResult;
import com.ideapipeline.model.PipelineOutput;
import com.ideapipeline.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidationOrchestratorTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private PipelineProperties pipelineProperties;

    private ValidationOrchestrator validationOrchestrator;

    @BeforeEach
    void setUp() {
        validationOrchestrator = new ValidationOrchestrator(llmClient, pipelineProperties);
    }

    @Test
    void validate_shouldReturnParsedValidationResult() {
        when(pipelineProperties.getConvergenceThreshold()).thenReturn(75);
        PipelineOutput docs = new PipelineOutput("context", "flow", "task");
        CritiqueResult critique = new CritiqueResult(List.of(), List.of(), List.of());
        String llmResponse = "{\"convergenceScore\": 85, \"shouldLoop\": false, \"reasoning\": \"good\"}";
        when(llmClient.chat(anyString(), anyString())).thenReturn(llmResponse);

        ValidationResult result = validationOrchestrator.validate(docs, critique, 0);

        assertEquals(85, result.convergenceScore());
        assertFalse(result.shouldLoop());
        assertEquals("good", result.reasoning());
    }

    @Test
    void validate_shouldHandleMarkdownFencedResponse() {
        when(pipelineProperties.getConvergenceThreshold()).thenReturn(75);
        PipelineOutput docs = new PipelineOutput("context", "flow", "task");
        CritiqueResult critique = new CritiqueResult(List.of(), List.of(), List.of());
        String llmResponse = "```json\n{\"convergenceScore\": 90, \"shouldLoop\": false, \"reasoning\": \"excellent\"}\n```";
        when(llmClient.chat(anyString(), anyString())).thenReturn(llmResponse);

        ValidationResult result = validationOrchestrator.validate(docs, critique, 0);

        assertEquals(90, result.convergenceScore());
        assertFalse(result.shouldLoop());
        assertEquals("excellent", result.reasoning());
    }

    @Test
    void validate_shouldUseDefaultsForMissingFields() {
        when(pipelineProperties.getConvergenceThreshold()).thenReturn(75);
        PipelineOutput docs = new PipelineOutput("context", "flow", "task");
        CritiqueResult critique = new CritiqueResult(List.of(), List.of(), List.of());
        String llmResponse = "{\"convergenceScore\": 50}";
        when(llmClient.chat(anyString(), anyString())).thenReturn(llmResponse);

        ValidationResult result = validationOrchestrator.validate(docs, critique, 0);

        assertEquals(50, result.convergenceScore());
        assertTrue(result.shouldLoop());
        assertEquals("", result.reasoning());
    }

    @Test
    void validate_shouldThrowPipelineException_onNullDocs() {
        CritiqueResult critique = new CritiqueResult(List.of(), List.of(), List.of());

        PipelineException exception = assertThrows(PipelineException.class, () -> {
            validationOrchestrator.validate(null, critique, 0);
        });

        assertEquals("Phase3b", exception.getPhase());
        assertEquals("docs is null", exception.getMessage());
    }

    @Test
    void validate_shouldThrowPipelineException_onNullCritique() {
        PipelineOutput docs = new PipelineOutput("context", "flow", "task");

        PipelineException exception = assertThrows(PipelineException.class, () -> {
            validationOrchestrator.validate(docs, null, 2);
        });

        assertEquals("Phase3b", exception.getPhase());
        assertEquals("critique is null", exception.getMessage());
    }

    @Test
    void validate_shouldThrowPipelineException_onNegativeLoopCount() {
        PipelineOutput docs = new PipelineOutput("context", "flow", "task");
        CritiqueResult critique = new CritiqueResult(List.of(), List.of(), List.of());

        PipelineException exception = assertThrows(PipelineException.class, () -> {
            validationOrchestrator.validate(docs, critique, -1);
        });

        assertEquals("Phase3b", exception.getPhase());
        assertEquals("loopCount must be >= 0", exception.getMessage());
    }

    @Test
    void validate_shouldThrowPipelineException_onEmptyLlmResponse() {
        PipelineOutput docs = new PipelineOutput("context", "flow", "task");
        CritiqueResult critique = new CritiqueResult(List.of(), List.of(), List.of());
        when(llmClient.chat(anyString(), anyString())).thenReturn("");

        PipelineException exception = assertThrows(PipelineException.class, () -> {
            validationOrchestrator.validate(docs, critique, 1);
        });

        assertEquals("Phase3b", exception.getPhase());
        assertEquals("Empty response from validation LLM", exception.getMessage());
    }

    @Test
    void validate_shouldThrowPipelineException_onInvalidJson() {
        PipelineOutput docs = new PipelineOutput("context", "flow", "task");
        CritiqueResult critique = new CritiqueResult(List.of(), List.of(), List.of());
        when(llmClient.chat(anyString(), anyString())).thenReturn("not json");

        PipelineException exception = assertThrows(PipelineException.class, () -> {
            validationOrchestrator.validate(docs, critique, 1);
        });

        assertEquals("Phase3b", exception.getPhase());
        assertEquals("Failed to parse validation JSON", exception.getMessage());
    }

    @Test
    void validate_shouldIncludeLoopCountInUserMessage() {
        when(pipelineProperties.getConvergenceThreshold()).thenReturn(75);
        PipelineOutput docs = new PipelineOutput("context", "flow", "task");
        CritiqueResult critique = new CritiqueResult(List.of(), List.of(), List.of());
        when(llmClient.chat(anyString(), anyString())).thenReturn("{\"convergenceScore\": 80, \"shouldLoop\": false, \"reasoning\": \"ok\"}");

        validationOrchestrator.validate(docs, critique, 2);

        ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).chat(anyString(), userMessageCaptor.capture());

        String userMessage = userMessageCaptor.getValue();
        assertTrue(userMessage.contains("iteration 2"), "User message should contain 'iteration 2'");
    }

    @Test
    void validate_shouldFormatCritiqueItemsAsBulletLines() {
        when(pipelineProperties.getConvergenceThreshold()).thenReturn(75);
        PipelineOutput docs = new PipelineOutput("context", "flow", "task");
        List<String> critiques = List.of("C1", "C2");
        List<String> gaps = List.of("G1");
        List<String> refinementNeeds = new ArrayList<>();
        CritiqueResult critique = new CritiqueResult(critiques, gaps, refinementNeeds);
        when(llmClient.chat(anyString(), anyString())).thenReturn("{\"convergenceScore\": 80, \"shouldLoop\": false, \"reasoning\": \"ok\"}");

        validationOrchestrator.validate(docs, critique, 1);

        ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).chat(anyString(), userMessageCaptor.capture());

        String userMessage = userMessageCaptor.getValue();
        assertTrue(userMessage.contains("- C1\n- C2"), "User message should contain '- C1\\n- C2'");
        assertTrue(userMessage.contains("- G1"), "User message should contain '- G1'");
        assertTrue(userMessage.contains("- (none)"), "User message should contain '- (none)' for refinementNeeds");
    }

    @Test
    void validate_shouldRenderNoneForNullOrEmptyLists() {
        when(pipelineProperties.getConvergenceThreshold()).thenReturn(75);
        PipelineOutput docs = new PipelineOutput("context", "flow", "task");
        CritiqueResult critique = new CritiqueResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        when(llmClient.chat(anyString(), anyString())).thenReturn("{\"convergenceScore\": 80, \"shouldLoop\": false, \"reasoning\": \"ok\"}");

        validationOrchestrator.validate(docs, critique, 1);

        ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).chat(anyString(), userMessageCaptor.capture());

        String userMessage = userMessageCaptor.getValue();
        int noneCount = countOccurrences(userMessage, "- (none)");
        assertEquals(3, noneCount, "Should have '- (none)' exactly 3 times for all three lists");
    }

    private int countOccurrences(String str, String substr) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(substr, idx)) != -1) {
            count++;
            idx += substr.length();
        }
        return count;
    }
}