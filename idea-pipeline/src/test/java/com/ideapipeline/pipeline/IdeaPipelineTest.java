package com.ideapipeline.pipeline;

import com.ideapipeline.config.PipelineProperties;
import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.*;
import com.ideapipeline.model.enums.BaseRole;
import com.ideapipeline.model.enums.Seniority;
import com.ideapipeline.orchestrator.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class IdeaPipelineTest {

    @Mock
    private GatekeeperOrchestrator gatekeeperOrchestrator;

    @Mock
    private DebateOrchestrator debateOrchestrator;

    @Mock
    private SynthesisOrchestrator synthesisOrchestrator;

    @Mock
    private CriticOrchestrator criticOrchestrator;

    @Mock
    private ValidationOrchestrator validationOrchestrator;

    @Mock
    private StackArchitectOrchestrator stackArchitectOrchestrator;

    @Mock
    private ScrumMasterOrchestrator scrumMasterOrchestrator;

    @Mock
    private PipelineProperties pipelineProperties;

    @InjectMocks
    private IdeaPipeline ideaPipeline;

    private static final RawIdea VALID_RAW_IDEA = new RawIdea("Build a multi-agent idea pipeline");
    private static final List<RefinementQA> VALID_REFINEMENTS = List.of(
            new RefinementQA("Who is the target user?", "Developers")
    );
    private static final Team VALID_TEAM = new Team(List.of(
            new TeamMember("tm-1", "Alice", BaseRole.BACKEND_DEV, Seniority.SENIOR, List.of())
    ));
    private static final IdeaContext VALID_CONTEXT = new IdeaContext(
            "Build a multi-agent idea pipeline",
            VALID_REFINEMENTS,
            "Enriched summary for the idea pipeline project"
    );
    private static final List<DebateMessage> VALID_DEBATE_HISTORY = List.of(
            new DebateMessage("ProductAgent", "Focus on user value", 1),
            new DebateMessage("ArchitectAgent", "Consider scalability", 1)
    );
    private static final PipelineOutput VALID_OUTPUT = new PipelineOutput(
            "Context doc content",
            "Flow doc content",
            "## Epic 1: Auth\n### User Story: As a user\n#### Task: Login\n- Description: Auth"
    );
    private static final CritiqueResult VALID_CRITIQUE = new CritiqueResult(
            List.of("Missing error handling"),
            List.of("gap1"),
            List.of("need1")
    );
    private static final ArchitectureDoc VALID_ARCHITECTURE = new ArchitectureDoc(
            "Java 21 + Spring Boot",
            List.of("API Gateway"),
            List.of("Must scale"),
            "Docker",
            "Team familiarity"
    );
    private static final TaskGraph VALID_TASK_GRAPH = new TaskGraph(
            List.of(new EstimatedTask(
                    new Task("task-001", "Login", "Auth", "epic-1", "As a user"),
                    5,
                    java.util.Map.of("Alice", 5),
                    List.of()
            )),
            java.util.Map.of("task-001", List.of()),
            5,
            java.util.Map.of("BACKEND_DEV", 5)
    );

    private static ValidationResult mockValidationResult(int score, boolean shouldLoop) {
        return new ValidationResult(score, shouldLoop, "reason");
    }

    @BeforeEach
    void setUp() {
        lenient().when(pipelineProperties.getConvergenceThreshold()).thenReturn(75);
        lenient().when(pipelineProperties.getMaxLoops()).thenReturn(3);
        lenient().when(pipelineProperties.getDefaultDebateRounds()).thenReturn(2);
    }

    @Test
    void run_shouldExecuteFullPipelineAndReturnResult() throws Exception {
        when(gatekeeperOrchestrator.buildContext(any(), any())).thenReturn(VALID_CONTEXT);
        when(debateOrchestrator.runDebate(any(), anyInt())).thenReturn(VALID_DEBATE_HISTORY);
        when(synthesisOrchestrator.synthesize(any(), any())).thenReturn(VALID_OUTPUT);
        when(criticOrchestrator.critique(any(), any())).thenReturn(VALID_CRITIQUE);
        when(validationOrchestrator.validate(any(), any(), anyInt())).thenReturn(mockValidationResult(85, false));
        when(stackArchitectOrchestrator.defineStack(any(), any(), any())).thenReturn(VALID_ARCHITECTURE);
        when(scrumMasterOrchestrator.estimateTasks(any(), any(), any())).thenReturn(VALID_TASK_GRAPH);

        PipelineResult result = ideaPipeline.run(VALID_RAW_IDEA, VALID_REFINEMENTS, VALID_TEAM);

        assertNotNull(result);
        assertEquals(0, result.loopsRequired());
        assertNotNull(result.taskGraph());
        assertEquals(VALID_CONTEXT, result.context());
        assertEquals(VALID_OUTPUT, result.documents());
        assertEquals(VALID_ARCHITECTURE, result.architecture());
    }

    @Test
    void run_shouldCallPhasesInOrder() throws Exception {
        when(gatekeeperOrchestrator.buildContext(any(), any())).thenReturn(VALID_CONTEXT);
        when(debateOrchestrator.runDebate(any(), anyInt())).thenReturn(VALID_DEBATE_HISTORY);
        when(synthesisOrchestrator.synthesize(any(), any())).thenReturn(VALID_OUTPUT);
        when(criticOrchestrator.critique(any(), any())).thenReturn(VALID_CRITIQUE);
        when(validationOrchestrator.validate(any(), any(), anyInt())).thenReturn(mockValidationResult(85, false));
        when(stackArchitectOrchestrator.defineStack(any(), any(), any())).thenReturn(VALID_ARCHITECTURE);
        when(scrumMasterOrchestrator.estimateTasks(any(), any(), any())).thenReturn(VALID_TASK_GRAPH);

        ideaPipeline.run(VALID_RAW_IDEA, VALID_REFINEMENTS, VALID_TEAM);

        InOrder inOrder = inOrder(
                gatekeeperOrchestrator, debateOrchestrator, synthesisOrchestrator,
                criticOrchestrator, validationOrchestrator, stackArchitectOrchestrator,
                scrumMasterOrchestrator
        );

        inOrder.verify(gatekeeperOrchestrator).buildContext(any(), any());
        inOrder.verify(debateOrchestrator).runDebate(any(), anyInt());
        inOrder.verify(synthesisOrchestrator).synthesize(any(), any());
        inOrder.verify(criticOrchestrator).critique(any(), any());
        inOrder.verify(validationOrchestrator).validate(any(), any(), anyInt());
        inOrder.verify(stackArchitectOrchestrator).defineStack(any(), any(), any());
        inOrder.verify(scrumMasterOrchestrator).estimateTasks(any(), any(), any());
    }

    @Test
    void run_shouldLoopOnceThenConverge() throws Exception {
        IdeaContext enrichedContext = new IdeaContext(
                "Build a multi-agent idea pipeline",
                VALID_REFINEMENTS,
                "Enriched summary\n\n--- Refinement round 1 ---\nGaps: gap1\nNeeds: need1"
        );
        List<DebateMessage> secondDebateHistory = List.of(
                new DebateMessage("ProductAgent", "Refined point", 1)
        );
        PipelineOutput secondOutput = new PipelineOutput("Context 2", "Flow 2", "Tasks 2");

        when(gatekeeperOrchestrator.buildContext(any(), any())).thenReturn(VALID_CONTEXT);
        when(debateOrchestrator.runDebate(any(), anyInt()))
                .thenReturn(VALID_DEBATE_HISTORY)
                .thenReturn(secondDebateHistory);
        when(synthesisOrchestrator.synthesize(any(), any()))
                .thenReturn(VALID_OUTPUT)
                .thenReturn(secondOutput);
        when(criticOrchestrator.critique(any(), any())).thenReturn(VALID_CRITIQUE);
        when(validationOrchestrator.validate(any(), any(), anyInt()))
                .thenReturn(mockValidationResult(50, true))
                .thenReturn(mockValidationResult(85, false));
        when(stackArchitectOrchestrator.defineStack(any(), any(), any())).thenReturn(VALID_ARCHITECTURE);
        when(scrumMasterOrchestrator.estimateTasks(any(), any(), any())).thenReturn(VALID_TASK_GRAPH);

        PipelineResult result = ideaPipeline.run(VALID_RAW_IDEA, VALID_REFINEMENTS, VALID_TEAM);

        assertEquals(1, result.loopsRequired());
        verify(debateOrchestrator, times(2)).runDebate(any(), anyInt());
        verify(synthesisOrchestrator, times(2)).synthesize(any(), any());
    }

    @Test
    void run_shouldForceConvergenceAfterMaxLoops() throws Exception {
        when(gatekeeperOrchestrator.buildContext(any(), any())).thenReturn(VALID_CONTEXT);
        when(debateOrchestrator.runDebate(any(), anyInt())).thenReturn(VALID_DEBATE_HISTORY);
        when(synthesisOrchestrator.synthesize(any(), any())).thenReturn(VALID_OUTPUT);
        when(criticOrchestrator.critique(any(), any())).thenReturn(VALID_CRITIQUE);
        when(validationOrchestrator.validate(any(), any(), anyInt()))
                .thenReturn(mockValidationResult(40, true))
                .thenReturn(mockValidationResult(40, true))
                .thenReturn(mockValidationResult(40, true));
        when(stackArchitectOrchestrator.defineStack(any(), any(), any())).thenReturn(VALID_ARCHITECTURE);
        when(scrumMasterOrchestrator.estimateTasks(any(), any(), any())).thenReturn(VALID_TASK_GRAPH);

        PipelineResult result = ideaPipeline.run(VALID_RAW_IDEA, VALID_REFINEMENTS, VALID_TEAM);

        verify(stackArchitectOrchestrator).defineStack(any(), any(), any());
        assertEquals(2, result.loopsRequired());
    }

    @Test
    void run_shouldEnrichIdeaContextWithGapsOnLoop() throws Exception {
        when(gatekeeperOrchestrator.buildContext(any(), any())).thenReturn(VALID_CONTEXT);
        when(debateOrchestrator.runDebate(any(), anyInt())).thenReturn(VALID_DEBATE_HISTORY);
        when(synthesisOrchestrator.synthesize(any(), any())).thenReturn(VALID_OUTPUT);
        when(criticOrchestrator.critique(any(), any())).thenReturn(VALID_CRITIQUE);
        when(validationOrchestrator.validate(any(), any(), anyInt()))
                .thenReturn(mockValidationResult(50, true))
                .thenReturn(mockValidationResult(85, false));
        when(stackArchitectOrchestrator.defineStack(any(), any(), any())).thenReturn(VALID_ARCHITECTURE);
        when(scrumMasterOrchestrator.estimateTasks(any(), any(), any())).thenReturn(VALID_TASK_GRAPH);

        ideaPipeline.run(VALID_RAW_IDEA, VALID_REFINEMENTS, VALID_TEAM);

        ArgumentCaptor<IdeaContext> contextCaptor = ArgumentCaptor.forClass(IdeaContext.class);
        verify(debateOrchestrator, times(2)).runDebate(contextCaptor.capture(), anyInt());

        IdeaContext secondContext = contextCaptor.getAllValues().get(1);
        assertTrue(secondContext.enrichedSummary().contains("gap1"));
        assertTrue(secondContext.enrichedSummary().contains("need1"));
    }

    @Test
    void run_shouldPassLoopCountToValidate() throws Exception {
        when(gatekeeperOrchestrator.buildContext(any(), any())).thenReturn(VALID_CONTEXT);
        when(debateOrchestrator.runDebate(any(), anyInt())).thenReturn(VALID_DEBATE_HISTORY);
        when(synthesisOrchestrator.synthesize(any(), any())).thenReturn(VALID_OUTPUT);
        when(criticOrchestrator.critique(any(), any())).thenReturn(VALID_CRITIQUE);
        when(validationOrchestrator.validate(any(), any(), anyInt()))
                .thenReturn(mockValidationResult(50, true))
                .thenReturn(mockValidationResult(85, false));
        when(stackArchitectOrchestrator.defineStack(any(), any(), any())).thenReturn(VALID_ARCHITECTURE);
        when(scrumMasterOrchestrator.estimateTasks(any(), any(), any())).thenReturn(VALID_TASK_GRAPH);

        ideaPipeline.run(VALID_RAW_IDEA, VALID_REFINEMENTS, VALID_TEAM);

        ArgumentCaptor<Integer> loopCountCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(validationOrchestrator, times(2)).validate(any(), any(), loopCountCaptor.capture());

        List<Integer> loopCounts = loopCountCaptor.getAllValues();
        assertEquals(0, loopCounts.get(0));
        assertEquals(1, loopCounts.get(1));
    }

    @Test
    void run_shouldThrowPipelineException_onNullRawIdea() {
        PipelineException ex = assertThrows(PipelineException.class, () ->
                ideaPipeline.run(null, VALID_REFINEMENTS, VALID_TEAM));
        assertEquals("Phase0", ex.getPhase());
    }

    @Test
    void run_shouldThrowPipelineException_onBlankRawIdeaDescription() {
        PipelineException ex = assertThrows(PipelineException.class, () ->
                ideaPipeline.run(new RawIdea("   "), VALID_REFINEMENTS, VALID_TEAM));
        assertEquals("Phase0", ex.getPhase());
    }

    @Test
    void run_shouldThrowPipelineException_onNullTeam() {
        PipelineException ex = assertThrows(PipelineException.class, () ->
                ideaPipeline.run(VALID_RAW_IDEA, VALID_REFINEMENTS, null));
        assertEquals("Phase4", ex.getPhase());
    }

    @Test
    void run_shouldTreatNullRefinementsAsEmptyList() throws Exception {
        when(gatekeeperOrchestrator.buildContext(any(), any())).thenReturn(VALID_CONTEXT);
        when(debateOrchestrator.runDebate(any(), anyInt())).thenReturn(VALID_DEBATE_HISTORY);
        when(synthesisOrchestrator.synthesize(any(), any())).thenReturn(VALID_OUTPUT);
        when(criticOrchestrator.critique(any(), any())).thenReturn(VALID_CRITIQUE);
        when(validationOrchestrator.validate(any(), any(), anyInt())).thenReturn(mockValidationResult(85, false));
        when(stackArchitectOrchestrator.defineStack(any(), any(), any())).thenReturn(VALID_ARCHITECTURE);
        when(scrumMasterOrchestrator.estimateTasks(any(), any(), any())).thenReturn(VALID_TASK_GRAPH);

        PipelineResult result = ideaPipeline.run(VALID_RAW_IDEA, null, VALID_TEAM);

        assertNotNull(result);
        verify(gatekeeperOrchestrator).buildContext(any(), any());
    }

    @Test
    void run_shouldPropagateExceptionFromOrchestrator() throws Exception {
        when(gatekeeperOrchestrator.buildContext(any(), any())).thenReturn(VALID_CONTEXT);
        when(debateOrchestrator.runDebate(any(), anyInt())).thenReturn(VALID_DEBATE_HISTORY);
        when(synthesisOrchestrator.synthesize(any(), any()))
                .thenThrow(new PipelineException("synthesis failed", "Phase2", 0));

        PipelineException ex = assertThrows(PipelineException.class, () ->
                ideaPipeline.run(VALID_RAW_IDEA, VALID_REFINEMENTS, VALID_TEAM));
        assertEquals("synthesis failed", ex.getMessage());
        assertEquals("Phase2", ex.getPhase());
    }
}