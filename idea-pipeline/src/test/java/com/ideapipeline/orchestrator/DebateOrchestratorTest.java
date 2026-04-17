package com.ideapipeline.orchestrator;

import com.ideapipeline.client.LlmClient;
import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.DebateMessage;
import com.ideapipeline.model.IdeaContext;
import com.ideapipeline.model.RefinementQA;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DebateOrchestratorTest {

    @Mock
    private LlmClient llmClient;

    private DebateOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new DebateOrchestrator(llmClient);
    }

    @Test
    void runDebate_shouldReturnSeedAndRoundMessages() throws Exception {
        IdeaContext context = new IdeaContext(
                "Build a task app",
                List.of(new RefinementQA("Q1", "A1")),
                "Enriched summary"
        );
        when(llmClient.chatWithHistory(anyString(), any(), anyString())).thenReturn("Response");

        List<DebateMessage> result = orchestrator.runDebate(context, 2);

        assertEquals(7, result.size());
        assertEquals("System", result.get(0).agentName());
        assertEquals("Enriched summary", result.get(0).content());
        assertEquals(0, result.get(0).round());
        assertEquals(1, result.get(1).round());
        assertEquals(2, result.get(4).round());
    }

    @Test
    void runDebate_shouldInvokeThreeAgentsPerRound() throws Exception {
        IdeaContext context = new IdeaContext("Idea", List.of(), "Summary");
        when(llmClient.chatWithHistory(anyString(), any(), anyString())).thenReturn("Response");

        orchestrator.runDebate(context, 3);

        verify(llmClient, times(9)).chatWithHistory(anyString(), any(), anyString());
    }

    @Test
    void runDebate_shouldPassImmutableSnapshot() throws Exception {
        IdeaContext context = new IdeaContext("Idea", List.of(), "Summary");

        doAnswer(invocation -> {
            List<DebateMessage> snapshot = invocation.getArgument(1);
            assertThrows(UnsupportedOperationException.class,
                    () -> snapshot.add(new DebateMessage("X", "Y", 99)));
            return "Response";
        }).when(llmClient).chatWithHistory(anyString(), any(), anyString());

        orchestrator.runDebate(context, 1);
    }

    @Test
    void runDebate_shouldFallbackWhenResponseIsBlank() throws Exception {
        IdeaContext context = new IdeaContext("Idea", List.of(), "Summary");
        when(llmClient.chatWithHistory(anyString(), any(), anyString())).thenReturn("   ");

        List<DebateMessage> result = orchestrator.runDebate(context, 1);

        assertEquals(4, result.size());
        assertEquals("[No response]", result.get(1).content());
        assertEquals("[No response]", result.get(2).content());
        assertEquals("[No response]", result.get(3).content());
    }

    @Test
    void runDebate_shouldThrowPipelineException_onInvalidRounds() {
        IdeaContext context = new IdeaContext("Idea", List.of(), "Summary");

        PipelineException exception = assertThrows(
                PipelineException.class,
                () -> orchestrator.runDebate(context, 0)
        );

        assertEquals("rounds must be > 0", exception.getMessage());
        assertEquals("Phase1", exception.getPhase());
    }

    @Test
    void runDebate_shouldWrapLlmFailureAsPipelineException() {
        IdeaContext context = new IdeaContext("Idea", List.of(), "Summary");
        when(llmClient.chatWithHistory(anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("LLM down"));

        PipelineException exception = assertThrows(
                PipelineException.class,
                () -> orchestrator.runDebate(context, 1)
        );

        assertEquals("Debate failed", exception.getMessage());
        assertEquals("Phase1", exception.getPhase());
        assertNotNull(exception.getCause());
    }

    @Test
    void runDebate_shouldGrowSnapshotAcrossRounds() throws Exception {
        IdeaContext context = new IdeaContext("Idea", List.of(), "Summary");
        AtomicInteger roundCounter = new AtomicInteger(0);

        doAnswer(invocation -> {
            List<DebateMessage> snapshot = invocation.getArgument(1);
            if (roundCounter.get() < 3) {
                assertEquals(1, snapshot.size());
            } else {
                assertEquals(4, snapshot.size());
            }
            roundCounter.incrementAndGet();
            return "Response";
        }).when(llmClient).chatWithHistory(anyString(), any(), anyString());

        orchestrator.runDebate(context, 2);
    }
}
