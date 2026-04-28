package com.ideapipeline.cli;

import com.ideapipeline.model.*;
import com.ideapipeline.model.enums.BaseRole;
import com.ideapipeline.model.enums.Seniority;
import com.ideapipeline.orchestrator.GatekeeperOrchestrator;
import com.ideapipeline.pipeline.IdeaPipeline;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineRunnerTest {

    @Mock
    private IdeaPipeline ideaPipeline;

    @Mock
    private GatekeeperOrchestrator gatekeeperOrchestrator;

    private ObjectMapper objectMapper;

    @InjectMocks
    private PipelineRunner pipelineRunner;

    @TempDir
    Path tempDir;

    private InputStream originalIn;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        pipelineRunner = new PipelineRunner(ideaPipeline, gatekeeperOrchestrator, objectMapper);
        originalIn = System.in;
    }

    @AfterEach
    void tearDown() {
        System.setIn(originalIn);
    }

    private static final IdeaContext VALID_CONTEXT = new IdeaContext(
            "test idea", List.of(), "enriched summary"
    );
    private static final PipelineOutput VALID_OUTPUT = new PipelineOutput(
            "context doc", "flow doc", "task doc"
    );
    private static final ArchitectureDoc VALID_ARCHITECTURE = new ArchitectureDoc(
            "Java 21", List.of("API"), List.of("scale"), "Docker", "reasons"
    );
    private static final TaskGraph VALID_TASK_GRAPH = new TaskGraph(
            List.of(), java.util.Map.of(), 0, java.util.Map.of()
    );
    private static final Team VALID_TEAM = new Team(List.of(
            new TeamMember("tm-1", "Alice", BaseRole.BACKEND_DEV, Seniority.SENIOR, List.of())
    ));

    private Path createTeamJson() throws IOException {
        Path teamFile = tempDir.resolve("team.json");
        String json = """
                {
                  "members": [
                    {
                      "id": "tm-1",
                      "name": "Alice",
                      "baseRole": "BACKEND_DEV",
                      "seniority": "SENIOR",
                      "extraResponsibilities": []
                    }
                  ]
                }
                """;
        Files.writeString(teamFile, json);
        return teamFile;
    }

    private Path createRefinementsJson() throws IOException {
        Path refFile = tempDir.resolve("refinements.json");
        String json = """
                [
                  { "question": "Who is the target user?", "answer": "Developers" },
                  { "question": "What is the main constraint?", "answer": "Must be offline-first" }
                ]
                """;
        Files.writeString(refFile, json);
        return refFile;
    }

    private PipelineResult createResult() {
        return new PipelineResult(VALID_CONTEXT, VALID_OUTPUT, VALID_ARCHITECTURE, VALID_TASK_GRAPH, 0);
    }

    @Test
    void run_shouldExecutePipelineInDirectMode() throws Exception {
        Path teamFile = createTeamJson();
        PipelineResult result = createResult();
        when(ideaPipeline.run(any(), any(), any())).thenReturn(result);

        String[] args = {"--idea=My idea", "--team=" + teamFile.toString()};
        pipelineRunner.run(new DefaultApplicationArguments(args));

        verify(ideaPipeline).run(any(), any(), any());
    }

    @Test
    void run_shouldExecutePipelineWithRefinementsFile() throws Exception {
        Path teamFile = createTeamJson();
        Path refFile = createRefinementsJson();
        PipelineResult result = createResult();
        when(ideaPipeline.run(any(), any(), any())).thenReturn(result);

        String[] args = {"--idea=My idea", "--team=" + teamFile.toString(), "--refinements=" + refFile.toString()};
        pipelineRunner.run(new DefaultApplicationArguments(args));

        verify(ideaPipeline).run(any(), any(), any());
        verify(ideaPipeline).run(any(), argThat(refinements -> refinements != null && refinements.size() == 2), any());
    }

    @Test
    void run_shouldUseEmptyRefinementsWhenFlagAbsent() throws Exception {
        Path teamFile = createTeamJson();
        PipelineResult result = createResult();
        when(ideaPipeline.run(any(), any(), any())).thenReturn(result);

        String[] args = {"--idea=My idea", "--team=" + teamFile.toString()};
        pipelineRunner.run(new DefaultApplicationArguments(args));

        verify(ideaPipeline).run(any(), argThat(refinements -> refinements != null && refinements.isEmpty()), any());
    }

    @Test
    void run_shouldExecutePipelineInInteractiveMode() throws Exception {
        Path teamFile = createTeamJson();
        PipelineResult result = createResult();
        when(gatekeeperOrchestrator.generateRefinementQuestions(any())).thenReturn(List.of("Q1?", "Q2?"));
        when(ideaPipeline.run(any(), any(), any())).thenReturn(result);

        String input = "A1\nA2\n";
        System.setIn(new ByteArrayInputStream(input.getBytes()));

        String[] args = {"--idea=My idea", "--team=" + teamFile.toString(), "--interactive"};
        pipelineRunner.run(new DefaultApplicationArguments(args));

        verify(gatekeeperOrchestrator).generateRefinementQuestions(any());
        verify(ideaPipeline).run(any(), argThat(refs -> refs.size() == 2), any());
    }

    @Test
    void run_shouldIgnoreRefinementsFileInInteractiveMode() throws Exception {
        Path teamFile = createTeamJson();
        Path refFile = createRefinementsJson();
        PipelineResult result = createResult();
        when(gatekeeperOrchestrator.generateRefinementQuestions(any())).thenReturn(List.of("Q1?"));
        when(ideaPipeline.run(any(), any(), any())).thenReturn(result);

        String input = "A1\n";
        System.setIn(new ByteArrayInputStream(input.getBytes()));

        String[] args = {"--idea=My idea", "--team=" + teamFile.toString(), "--interactive", "--refinements=" + refFile.toString()};
        pipelineRunner.run(new DefaultApplicationArguments(args));

        verify(gatekeeperOrchestrator).generateRefinementQuestions(any());
        verify(ideaPipeline).run(any(), argThat(refs -> refs.size() == 1), any());
    }

    @Test
    void run_shouldWriteResultToDefaultOutputFile() throws Exception {
        Path teamFile = createTeamJson();
        PipelineResult result = createResult();
        when(ideaPipeline.run(any(), any(), any())).thenReturn(result);

        String[] args = {"--idea=My idea", "--team=" + teamFile.toString()};
        pipelineRunner.run(new DefaultApplicationArguments(args));

        File defaultFile = new File("pipeline-result.json");
        try {
            assertTrue(defaultFile.exists());
            String content = Files.readString(defaultFile.toPath());
            assertTrue(content.contains("enriched summary"));
        } finally {
            defaultFile.delete();
        }
    }

    @Test
    void run_shouldWriteResultToCustomOutputFile() throws Exception {
        Path teamFile = createTeamJson();
        Path outputFile = tempDir.resolve("my-result.json");
        PipelineResult result = createResult();
        when(ideaPipeline.run(any(), any(), any())).thenReturn(result);

        String[] args = {"--idea=My idea", "--team=" + teamFile.toString(), "--output=" + outputFile.toString()};
        pipelineRunner.run(new DefaultApplicationArguments(args));

        assertTrue(Files.exists(outputFile));
        String content = Files.readString(outputFile);
        assertTrue(content.contains("enriched summary"));
    }

    @Test
    void parseTeam_shouldDeserializeValidJson() throws Exception {
        Path teamFile = createTeamJson();

        Team team = pipelineRunner.parseTeam(teamFile.toString());

        assertEquals(1, team.members().size());
        assertEquals("Alice", team.members().get(0).name());
        assertEquals(BaseRole.BACKEND_DEV, team.members().get(0).baseRole());
        assertEquals(Seniority.SENIOR, team.members().get(0).seniority());
    }

    @Test
    void parseRefinements_shouldDeserializeValidJson() throws Exception {
        Path refFile = createRefinementsJson();

        List<RefinementQA> refinements = pipelineRunner.parseRefinements(refFile.toString());

        assertEquals(2, refinements.size());
        assertEquals("Who is the target user?", refinements.get(0).question());
        assertEquals("Developers", refinements.get(0).answer());
        assertEquals("What is the main constraint?", refinements.get(1).question());
    }

    @Test
    void readAnswersFromConsole_shouldBuildRefinementQAList() {
        String input = "Answer1\nAnswer2\n";
        System.setIn(new ByteArrayInputStream(input.getBytes()));
        List<String> questions = List.of("Q1?", "Q2?");

        List<RefinementQA> result = pipelineRunner.readAnswersFromConsole(questions);

        assertEquals(2, result.size());
        assertEquals("Q1?", result.get(0).question());
        assertEquals("Answer1", result.get(0).answer());
        assertEquals("Q2?", result.get(1).question());
        assertEquals("Answer2", result.get(1).answer());
    }
}