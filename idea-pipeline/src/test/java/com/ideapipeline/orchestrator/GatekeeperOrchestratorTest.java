package com.ideapipeline.orchestrator;

import com.ideapipeline.client.LlmClient;
import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatekeeperOrchestratorTest {

    @Mock
    private LlmClient llmClient;

    private GatekeeperOrchestrator gatekeeper;

    @BeforeEach
    void setUp() {
        gatekeeper = new GatekeeperOrchestrator(llmClient);
    }

    @Test
    void generateRefinementQuestions_shouldReturnParsedQuestions() {
        RawIdea idea = new RawIdea("A mobile app for tracking habits");
        String llmResponse = "{\"questions\": [\"q1\", \"q2\", \"q3\"]}";
        when(llmClient.chat(anyString(), eq(idea.description()))).thenReturn(llmResponse);

        List<String> result = gatekeeper.generateRefinementQuestions(idea);

        assertEquals(3, result.size());
        assertEquals("q1", result.get(0));
        assertEquals("q2", result.get(1));
        assertEquals("q3", result.get(2));
    }

    @Test
    void generateRefinementQuestions_shouldThrowPipelineException_onInvalidJson() {
        RawIdea idea = new RawIdea("Some idea");
        when(llmClient.chat(anyString(), anyString())).thenReturn("not valid json");

        PipelineException exception = assertThrows(PipelineException.class, () -> {
            gatekeeper.generateRefinementQuestions(idea);
        });

        assertEquals("Phase0", exception.getPhase());
    }

    @Test
    void generateRefinementQuestions_shouldHandleMarkdownFencedResponse() {
        RawIdea idea = new RawIdea("Some idea");
        String llmResponse = "```json\n{\"questions\": [\"q1\"]}\n```";
        when(llmClient.chat(anyString(), anyString())).thenReturn(llmResponse);

        List<String> result = gatekeeper.generateRefinementQuestions(idea);

        assertEquals(1, result.size());
        assertEquals("q1", result.get(0));
    }

    @Test
    void buildContext_shouldReturnEnrichedIdeaContext() {
        RawIdea rawIdea = new RawIdea("A mobile app for tracking habits");
        List<RefinementQA> answers = new ArrayList<>();
        answers.add(new RefinementQA("Who is the target user?", "Busy professionals"));
        when(llmClient.chat(anyString(), anyString())).thenReturn("Enriched summary text");

        IdeaContext result = gatekeeper.buildContext(rawIdea, answers);

        assertEquals(rawIdea.description(), result.rawIdea());
        assertEquals("Enriched summary text", result.enrichedSummary());
        assertEquals(answers, result.refinements());
    }

    @Test
    void buildContext_shouldFormatQABlockCorrectly() {
        RawIdea rawIdea = new RawIdea("A mobile app for tracking habits");
        List<RefinementQA> answers = new ArrayList<>();
        answers.add(new RefinementQA("Who is the target user?", "Busy professionals"));
        when(llmClient.chat(anyString(), anyString())).thenReturn("Summary");

        gatekeeper.buildContext(rawIdea, answers);

        verify(llmClient).chat(anyString(), argThat(msg ->
                msg.contains("Q: Who is the target user?\nA: Busy professionals")
        ));
    }

    @Test
    void buildContext_shouldHandleEmptyAnswers() {
        RawIdea rawIdea = new RawIdea("A mobile app for tracking habits");
        List<RefinementQA> answers = new ArrayList<>();
        when(llmClient.chat(anyString(), anyString())).thenReturn("Summary without answers");

        IdeaContext result = gatekeeper.buildContext(rawIdea, answers);

        assertNotNull(result);
        assertEquals(rawIdea.description(), result.rawIdea());
        assertEquals("Summary without answers", result.enrichedSummary());
        assertTrue(result.refinements().isEmpty());
    }

    @Test
    void generateRefinementQuestions_shouldThrowPipelineException_onEmptyResponse() {
        RawIdea idea = new RawIdea("Some idea");
        when(llmClient.chat(anyString(), anyString())).thenReturn("");

        PipelineException exception = assertThrows(PipelineException.class, () -> {
            gatekeeper.generateRefinementQuestions(idea);
        });

        assertEquals("Phase0", exception.getPhase());
    }

    @Test
    void buildContext_shouldThrowPipelineException_onEmptyResponse() {
        RawIdea rawIdea = new RawIdea("A mobile app for tracking habits");
        List<RefinementQA> answers = new ArrayList<>();
        when(llmClient.chat(anyString(), anyString())).thenReturn("");

        PipelineException exception = assertThrows(PipelineException.class, () -> {
            gatekeeper.buildContext(rawIdea, answers);
        });

        assertEquals("Phase0", exception.getPhase());
    }
}