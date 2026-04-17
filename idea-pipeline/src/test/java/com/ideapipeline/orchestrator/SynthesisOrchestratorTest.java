package com.ideapipeline.orchestrator;

import com.ideapipeline.client.LlmClient;
import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.DebateMessage;
import com.ideapipeline.model.IdeaContext;
import com.ideapipeline.model.PipelineOutput;
import com.ideapipeline.model.RefinementQA;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SynthesisOrchestratorTest {

    @Mock
    private LlmClient llmClient;

    private SynthesisOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new SynthesisOrchestrator(llmClient);
    }

    @Test
    void synthesize_shouldReturnPipelineOutputWithAllThreeDocuments() throws Exception {
        IdeaContext context = new IdeaContext(
                "Build a task app",
                List.of(new RefinementQA("Q1", "A1")),
                "Enriched summary for task app"
        );
        List<DebateMessage> history = List.of(
                new DebateMessage("ProductAgent", "User-centric design is key", 1),
                new DebateMessage("ArchitectAgent", "Consider using cloud services", 1)
        );

        when(llmClient.chat(contains("technical writer"), anyString())).thenReturn("Context doc");
        when(llmClient.chat(contains("business analyst"), anyString())).thenReturn("Flow doc");
        when(llmClient.chat(contains("tech lead"), anyString())).thenReturn("Task doc");

        PipelineOutput result = orchestrator.synthesize(context, history);

        assertEquals("Context doc", result.contextDocument());
        assertEquals("Flow doc", result.flowDocument());
        assertEquals("Task doc", result.taskDocument());
    }

    @Test
    void synthesize_shouldCallLlmThreeTimes() throws Exception {
        IdeaContext context = new IdeaContext(
                "Build a task app",
                List.of(),
                "Summary"
        );
        List<DebateMessage> history = List.of(
                new DebateMessage("Agent1", "Content1", 1)
        );

        when(llmClient.chat(anyString(), anyString())).thenReturn("doc");

        orchestrator.synthesize(context, history);

        verify(llmClient, times(3)).chat(anyString(), anyString());
    }

    @Test
    void synthesize_shouldThrowPipelineException_onNullContext() {
        List<DebateMessage> history = List.of(
                new DebateMessage("Agent", "Content", 1)
        );

        PipelineException exception = assertThrows(
                PipelineException.class,
                () -> orchestrator.synthesize(null, history)
        );

        assertEquals("Phase2", exception.getPhase());
        assertTrue(exception.getMessage().contains("context"));
    }

    @Test
    void synthesize_shouldSucceedWithEmptyHistory() throws Exception {
        IdeaContext context = new IdeaContext(
                "Build a task app",
                List.of(),
                "Enriched summary"
        );

        when(llmClient.chat(anyString(), anyString())).thenReturn("doc");

        PipelineOutput result = orchestrator.synthesize(context, List.of());

        assertNotNull(result);
        assertEquals("doc", result.contextDocument());
        assertEquals("doc", result.flowDocument());
        assertEquals("doc", result.taskDocument());

        ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient, times(3)).chat(anyString(), userMessageCaptor.capture());
        assertTrue(userMessageCaptor.getValue().contains("No debate history available"));
    }

    @Test
    void synthesize_shouldThrowPipelineException_onEmptyDocumentResponse() {
        IdeaContext context = new IdeaContext(
                "Build a task app",
                List.of(),
                "Summary"
        );
        List<DebateMessage> history = List.of(
                new DebateMessage("Agent", "Content", 1)
        );

        when(llmClient.chat(anyString(), anyString())).thenReturn("");

        PipelineException exception = assertThrows(
                PipelineException.class,
                () -> orchestrator.synthesize(context, history)
        );

        assertEquals("Phase2", exception.getPhase());
        assertTrue(exception.getMessage().contains("Empty document"));
    }

    @Test
    void synthesize_shouldFormatDebateHistoryInUserMessage() throws Exception {
        IdeaContext context = new IdeaContext(
                "Build a task app",
                List.of(),
                "Enriched summary"
        );
        List<DebateMessage> history = List.of(
                new DebateMessage("ProductAgent", "Focus on user needs", 1),
                new DebateMessage("ArchitectAgent", "Use microservices", 1),
                new DebateMessage("CriticAgent", "Consider security risks", 1)
        );

        when(llmClient.chat(anyString(), anyString())).thenReturn("doc");

        orchestrator.synthesize(context, history);

        ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient, times(3)).chat(anyString(), userMessageCaptor.capture());

        // All 3 calls should receive the same userMessage with formatted debate history
        List<String> allMessages = userMessageCaptor.getAllValues();
        String userMessage = allMessages.getFirst();
        assertTrue(userMessage.contains("ProductAgent: Focus on user needs"));
        assertTrue(userMessage.contains("ArchitectAgent: Use microservices"));
        assertTrue(userMessage.contains("CriticAgent: Consider security risks"));
    }

    @Test
    void synthesize_shouldIncludeEnrichedSummaryInUserMessage() throws Exception {
        IdeaContext context = new IdeaContext(
                "Build a task app",
                List.of(),
                "This is the enriched summary about the task application"
        );
        List<DebateMessage> history = List.of(
                new DebateMessage("Agent", "Content", 1)
        );

        when(llmClient.chat(anyString(), anyString())).thenReturn("doc");

        orchestrator.synthesize(context, history);

        ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient, times(3)).chat(anyString(), userMessageCaptor.capture());

        // All 3 calls receive the same userMessage containing the enriched summary
        List<String> allMessages = userMessageCaptor.getAllValues();
        String userMessage = allMessages.getFirst();
        assertTrue(userMessage.contains("This is the enriched summary about the task application"));
    }

    @Test
    void synthesize_shouldThrowPipelineException_onLlmFailure() {
        IdeaContext context = new IdeaContext(
                "Build a task app",
                List.of(),
                "Summary"
        );
        List<DebateMessage> history = List.of(
                new DebateMessage("Agent", "Content", 1)
        );

        when(llmClient.chat(anyString(), anyString()))
                .thenThrow(new RuntimeException("LLM error"));

        PipelineException exception = assertThrows(
                PipelineException.class,
                () -> orchestrator.synthesize(context, history)
        );

        assertEquals("Phase2", exception.getPhase());
        assertTrue(exception.getMessage().contains("Synthesis"));
    }
}