package com.ideapipeline.orchestrator;

import com.ideapipeline.client.LlmClient;
import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.ArchitectureDoc;
import com.ideapipeline.model.DebateMessage;
import com.ideapipeline.model.IdeaContext;
import com.ideapipeline.model.PipelineOutput;
import com.ideapipeline.model.RefinementQA;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StackArchitectOrchestratorTest {

    @Mock
    private LlmClient llmClient;

    @InjectMocks
    private StackArchitectOrchestrator stackArchitectOrchestrator;

    private static final IdeaContext VALID_CONTEXT = new IdeaContext(
            "Build a multi-agent debate system",
            List.of(new RefinementQA("What is the target?", "Developers")),
            "A multi-agent system that transforms raw ideas into structured projects"
    );

    private static final PipelineOutput VALID_DOCUMENTS = new PipelineOutput(
            "Context document content",
            "Flow document content",
            "Task document content"
    );

    private static final String FULL_JSON_RESPONSE = """
            {
              "stack": "Java 21, Spring Boot 3.3, PostgreSQL, Redis, Docker",
              "services": ["API Gateway", "Auth Service", "Core Service"],
              "constraints": ["Must support Java 21", "Containerized deployment"],
              "deploymentModel": "Cloud-native container orchestration (Kubernetes)",
              "rationale": "Chosen for scalability and developer productivity"
            }
            """;

    @Test
    void defineStack_shouldReturnParsedArchitectureDoc() {
        when(llmClient.chat(anyString(), anyString())).thenReturn(FULL_JSON_RESPONSE);

        ArchitectureDoc result = stackArchitectOrchestrator.defineStack(VALID_CONTEXT, VALID_DOCUMENTS, List.of());

        assertEquals("Java 21, Spring Boot 3.3, PostgreSQL, Redis, Docker", result.stack());
        assertEquals(List.of("API Gateway", "Auth Service", "Core Service"), result.services());
        assertEquals(List.of("Must support Java 21", "Containerized deployment"), result.constraints());
        assertEquals("Cloud-native container orchestration (Kubernetes)", result.deploymentModel());
        assertEquals("Chosen for scalability and developer productivity", result.rationale());
    }

    @Test
    void defineStack_shouldHandleMarkdownFencedResponse() {
        String fencedResponse = "```json\n" + FULL_JSON_RESPONSE + "\n```";
        when(llmClient.chat(anyString(), anyString())).thenReturn(fencedResponse);

        ArchitectureDoc result = stackArchitectOrchestrator.defineStack(VALID_CONTEXT, VALID_DOCUMENTS, List.of());

        assertEquals("Java 21, Spring Boot 3.3, PostgreSQL, Redis, Docker", result.stack());
        assertEquals(3, result.services().size());
    }

    @Test
    void defineStack_shouldUseDefaultsForMissingFields() {
        String partialJson = """
                {"stack": "Java 21"}
                """;
        when(llmClient.chat(anyString(), anyString())).thenReturn(partialJson);

        ArchitectureDoc result = stackArchitectOrchestrator.defineStack(VALID_CONTEXT, VALID_DOCUMENTS, List.of());

        assertEquals("Java 21", result.stack());
        assertEquals(Collections.emptyList(), result.services());
        assertEquals(Collections.emptyList(), result.constraints());
        assertEquals("", result.deploymentModel());
        assertEquals("", result.rationale());
    }

    @Test
    void defineStack_shouldThrowPipelineException_onNullContext() {
        PipelineException ex = assertThrows(PipelineException.class, () ->
                stackArchitectOrchestrator.defineStack(null, VALID_DOCUMENTS, List.of()));
        assertEquals("Phase3.5", ex.getPhase());
    }

    @Test
    void defineStack_shouldThrowPipelineException_onEmptyEnrichedSummary() {
        IdeaContext emptyContext = new IdeaContext("idea", List.of(), "");

        PipelineException ex = assertThrows(PipelineException.class, () ->
                stackArchitectOrchestrator.defineStack(emptyContext, VALID_DOCUMENTS, List.of()));
        assertEquals("Phase3.5", ex.getPhase());
    }

    @Test
    void defineStack_shouldThrowPipelineException_onNullDocuments() {
        PipelineException ex = assertThrows(PipelineException.class, () ->
                stackArchitectOrchestrator.defineStack(VALID_CONTEXT, null, List.of()));
        assertEquals("Phase3.5", ex.getPhase());
    }

    @Test
    void defineStack_shouldThrowPipelineException_onEmptyDocument() {
        PipelineOutput emptyDocs = new PipelineOutput("", "flow", "task");

        PipelineException ex = assertThrows(PipelineException.class, () ->
                stackArchitectOrchestrator.defineStack(VALID_CONTEXT, emptyDocs, List.of()));
        assertEquals("Phase3.5", ex.getPhase());
    }

    @Test
    void defineStack_shouldThrowPipelineException_onEmptyLlmResponse() {
        when(llmClient.chat(anyString(), anyString())).thenReturn("");

        PipelineException ex = assertThrows(PipelineException.class, () ->
                stackArchitectOrchestrator.defineStack(VALID_CONTEXT, VALID_DOCUMENTS, List.of()));
        assertEquals("Phase3.5", ex.getPhase());
    }

    @Test
    void defineStack_shouldThrowPipelineException_onInvalidJson() {
        when(llmClient.chat(anyString(), anyString())).thenReturn("not json");

        PipelineException ex = assertThrows(PipelineException.class, () ->
                stackArchitectOrchestrator.defineStack(VALID_CONTEXT, VALID_DOCUMENTS, List.of()));
        assertEquals("Phase3.5", ex.getPhase());
    }

    @Test
    void defineStack_shouldIncludeAllSectionsInUserMessage() {
        when(llmClient.chat(anyString(), anyString())).thenReturn(FULL_JSON_RESPONSE);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        stackArchitectOrchestrator.defineStack(VALID_CONTEXT, VALID_DOCUMENTS, List.of());

        verify(llmClient).chat(anyString(), captor.capture());
        String userMessage = captor.getValue();

        assertTrue(userMessage.contains("=== IDEA CONTEXT ==="));
        assertTrue(userMessage.contains("=== CONTEXT DOCUMENT ==="));
        assertTrue(userMessage.contains("=== FLOW DOCUMENT ==="));
        assertTrue(userMessage.contains("=== TASK DOCUMENT ==="));
        assertTrue(userMessage.contains(VALID_CONTEXT.enrichedSummary()));
        assertTrue(userMessage.contains(VALID_DOCUMENTS.contextDocument()));
        assertTrue(userMessage.contains(VALID_DOCUMENTS.flowDocument()));
        assertTrue(userMessage.contains(VALID_DOCUMENTS.taskDocument()));
    }

    @Test
    void defineStack_shouldIncludeDebateHistoryInUserMessage() {
        when(llmClient.chat(anyString(), anyString())).thenReturn(FULL_JSON_RESPONSE);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        List<DebateMessage> history = List.of(
                new DebateMessage("ProductAgent", "Focus on user value", 1),
                new DebateMessage("ArchitectAgent", "Consider scalability", 1)
        );

        stackArchitectOrchestrator.defineStack(VALID_CONTEXT, VALID_DOCUMENTS, history);

        verify(llmClient).chat(anyString(), captor.capture());
        String userMessage = captor.getValue();

        assertTrue(userMessage.contains("=== DEBATE HISTORY ==="));
        assertTrue(userMessage.contains("ProductAgent: Focus on user value"));
        assertTrue(userMessage.contains("ArchitectAgent: Consider scalability"));
    }

    @Test
    void defineStack_shouldOmitDebateHistorySectionWhenHistoryIsEmpty() {
        when(llmClient.chat(anyString(), anyString())).thenReturn(FULL_JSON_RESPONSE);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        stackArchitectOrchestrator.defineStack(VALID_CONTEXT, VALID_DOCUMENTS, List.of());

        verify(llmClient).chat(anyString(), captor.capture());
        String userMessage = captor.getValue();

        assertFalse(userMessage.contains("=== DEBATE HISTORY ==="));
    }

    @Test
    void defineStack_shouldOmitDebateHistorySectionWhenHistoryIsNull() {
        when(llmClient.chat(anyString(), anyString())).thenReturn(FULL_JSON_RESPONSE);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        ArchitectureDoc result = stackArchitectOrchestrator.defineStack(VALID_CONTEXT, VALID_DOCUMENTS, null);

        verify(llmClient).chat(anyString(), captor.capture());
        String userMessage = captor.getValue();

        assertFalse(userMessage.contains("=== DEBATE HISTORY ==="));
        assertNotNull(result);
    }
}
