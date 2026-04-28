package com.ideapipeline.orchestrator;

import com.ideapipeline.client.LlmClient;
import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.CritiqueResult;
import com.ideapipeline.model.DebateMessage;
import com.ideapipeline.model.PipelineOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CriticOrchestratorTest {

    @Mock
    private LlmClient llmClient;

    private CriticOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new CriticOrchestrator(llmClient);
    }

    @Test
    void critique_shouldReturnParsedCritiqueResult() {
        PipelineOutput docs = validDocs();
        when(llmClient.chat(anyString(), anyString())).thenReturn("""
                {
                  "critiques": ["c1", "c2"],
                  "gaps": ["g1"],
                  "refinementNeeds": ["r1", "r2"]
                }
                """);

        CritiqueResult result = orchestrator.critique(docs, List.of());

        assertEquals(List.of("c1", "c2"), result.critiques());
        assertEquals(List.of("g1"), result.gaps());
        assertEquals(List.of("r1", "r2"), result.refinementNeeds());
    }

    @Test
    void critique_shouldHandleMarkdownFencedResponse() {
        PipelineOutput docs = validDocs();
        when(llmClient.chat(anyString(), anyString()))
                .thenReturn("```json\n{\"critiques\":[\"c1\"],\"gaps\":[],\"refinementNeeds\":[]}\n```");

        CritiqueResult result = orchestrator.critique(docs, List.of());

        assertEquals(List.of("c1"), result.critiques());
    }

    @Test
    void critique_shouldReturnEmptyListsForMissingArrays() {
        PipelineOutput docs = validDocs();
        when(llmClient.chat(anyString(), anyString())).thenReturn("{\"critiques\":[\"c1\"]}");

        CritiqueResult result = orchestrator.critique(docs, List.of());

        assertEquals(List.of("c1"), result.critiques());
        assertNotNull(result.gaps());
        assertNotNull(result.refinementNeeds());
        assertTrue(result.gaps().isEmpty());
        assertTrue(result.refinementNeeds().isEmpty());
    }

    @Test
    void critique_shouldThrowPipelineException_onNullDocs() {
        PipelineException exception = assertThrows(
                PipelineException.class,
                () -> orchestrator.critique(null, List.of())
        );

        assertEquals("Phase3a", exception.getPhase());
    }

    @Test
    void critique_shouldThrowPipelineException_onEmptyDocument() {
        PipelineOutput docs = new PipelineOutput(" ", "flow", "task");

        PipelineException exception = assertThrows(
                PipelineException.class,
                () -> orchestrator.critique(docs, List.of())
        );

        assertEquals("Phase3a", exception.getPhase());
    }

    @Test
    void critique_shouldThrowPipelineException_onEmptyLlmResponse() {
        PipelineOutput docs = validDocs();
        when(llmClient.chat(anyString(), anyString())).thenReturn("");

        PipelineException exception = assertThrows(
                PipelineException.class,
                () -> orchestrator.critique(docs, List.of())
        );

        assertEquals("Phase3a", exception.getPhase());
    }

    @Test
    void critique_shouldThrowPipelineException_onInvalidJson() {
        PipelineOutput docs = validDocs();
        when(llmClient.chat(anyString(), anyString())).thenReturn("not json");

        PipelineException exception = assertThrows(
                PipelineException.class,
                () -> orchestrator.critique(docs, List.of())
        );

        assertEquals("Phase3a", exception.getPhase());
    }

    @Test
    void critique_shouldFormatDocumentsInUserMessage() {
        PipelineOutput docs = validDocs();
        when(llmClient.chat(anyString(), anyString()))
                .thenReturn("{\"critiques\":[],\"gaps\":[],\"refinementNeeds\":[]}");

        orchestrator.critique(docs, List.of());

        ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).chat(anyString(), userMessageCaptor.capture());
        String userMessage = userMessageCaptor.getValue();
        assertTrue(userMessage.contains("=== CONTEXT DOCUMENT ==="));
        assertTrue(userMessage.contains("Context content"));
        assertTrue(userMessage.contains("=== FLOW DOCUMENT ==="));
        assertTrue(userMessage.contains("Flow content"));
        assertTrue(userMessage.contains("=== TASK DOCUMENT ==="));
        assertTrue(userMessage.contains("Task content"));
    }

    @Test
    void critique_shouldIncludeDebateHistoryInUserMessage() {
        PipelineOutput docs = validDocs();
        List<DebateMessage> history = List.of(
                new DebateMessage("ProductAgent", "Need clearer scope", 1),
                new DebateMessage("ArchitectAgent", "Define service boundaries", 1)
        );
        when(llmClient.chat(anyString(), anyString()))
                .thenReturn("{\"critiques\":[],\"gaps\":[],\"refinementNeeds\":[]}");

        orchestrator.critique(docs, history);

        ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).chat(anyString(), userMessageCaptor.capture());
        String userMessage = userMessageCaptor.getValue();
        assertTrue(userMessage.contains("=== DEBATE HISTORY ==="));
        assertTrue(userMessage.contains("ProductAgent: Need clearer scope"));
        assertTrue(userMessage.contains("ArchitectAgent: Define service boundaries"));
    }

    @Test
    void critique_shouldOmitDebateHistorySectionWhenHistoryIsEmpty() {
        PipelineOutput docs = validDocs();
        when(llmClient.chat(anyString(), anyString()))
                .thenReturn("{\"critiques\":[],\"gaps\":[],\"refinementNeeds\":[]}");

        orchestrator.critique(docs, List.of());

        ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).chat(anyString(), userMessageCaptor.capture());
        assertFalse(userMessageCaptor.getValue().contains("=== DEBATE HISTORY ==="));
    }

    @Test
    void critique_shouldOmitDebateHistorySectionWhenHistoryIsNull() {
        PipelineOutput docs = validDocs();
        when(llmClient.chat(anyString(), anyString()))
                .thenReturn("{\"critiques\":[],\"gaps\":[],\"refinementNeeds\":[]}");

        assertDoesNotThrow(() -> orchestrator.critique(docs, null));

        ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).chat(anyString(), userMessageCaptor.capture());
        assertFalse(userMessageCaptor.getValue().contains("=== DEBATE HISTORY ==="));
    }

    private PipelineOutput validDocs() {
        return new PipelineOutput("Context content", "Flow content", "Task content");
    }
}
