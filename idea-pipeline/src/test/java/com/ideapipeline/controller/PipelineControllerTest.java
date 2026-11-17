package com.ideapipeline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ideapipeline.controller.dto.*;
import com.ideapipeline.model.*;
import com.ideapipeline.model.enums.BaseRole;
import com.ideapipeline.model.enums.PipelineJobStatus;
import com.ideapipeline.model.enums.Seniority;
import com.ideapipeline.orchestrator.GatekeeperOrchestrator;
import com.ideapipeline.pipeline.PipelineJobService;
import com.ideapipeline.repository.PipelineJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineControllerTest {

    @Mock
    private PipelineJobService pipelineJobService;

    @Mock
    private PipelineJobRepository repository;

    @Mock
    private GatekeeperOrchestrator gatekeeperOrchestrator;

    private ObjectMapper objectMapper;

    @InjectMocks
    private PipelineController controller;

    private static final Team VALID_TEAM = new Team(List.of(
            new TeamMember("tm-1", "Alice", BaseRole.BACKEND_DEV, Seniority.SENIOR, List.of())
    ));

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new PipelineController(pipelineJobService, repository, gatekeeperOrchestrator, objectMapper);
    }

    @Test
    void postQuestions_shouldReturn200WithQuestions() {
        when(gatekeeperOrchestrator.generateRefinementQuestions(any()))
                .thenReturn(List.of("Q1?", "Q2?"));

        ResponseEntity<QuestionsResponse> response = controller.postQuestions(new QuestionsRequest("My idea"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().questions().size());
        assertEquals("Q1?", response.getBody().questions().get(0));
    }

    @Test
    void postQuestions_shouldReturn400WhenDescriptionIsBlank() {
        ResponseEntity<QuestionsResponse> response = controller.postQuestions(new QuestionsRequest(""));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void postRun_shouldReturn202WithJobId() {
        when(pipelineJobService.submitJob(any(), any(), any())).thenReturn("job-123");

        RunPipelineRequest request = new RunPipelineRequest("My idea", List.of(), VALID_TEAM);
        ResponseEntity<RunPipelineResponse> response = controller.postRun(request);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("job-123", response.getBody().jobId());
        assertEquals("QUEUED", response.getBody().status());
    }

    @Test
    void postRun_shouldReturn400WhenIdeaIsBlank() {
        RunPipelineRequest request = new RunPipelineRequest("", List.of(), VALID_TEAM);
        ResponseEntity<RunPipelineResponse> response = controller.postRun(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void postRun_shouldReturn400WhenTeamIsNull() {
        RunPipelineRequest request = new RunPipelineRequest("My idea", List.of(), null);
        ResponseEntity<RunPipelineResponse> response = controller.postRun(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void postRun_shouldReturn400WhenTeamMembersIsEmpty() {
        RunPipelineRequest request = new RunPipelineRequest("My idea", List.of(), new Team(List.of()));
        ResponseEntity<RunPipelineResponse> response = controller.postRun(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void postRun_shouldTreatNullRefinementsAsEmptyList() {
        when(pipelineJobService.submitJob(any(), any(), any())).thenReturn("job-456");

        RunPipelineRequest request = new RunPipelineRequest("My idea", null, VALID_TEAM);
        ResponseEntity<RunPipelineResponse> response = controller.postRun(request);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("job-456", response.getBody().jobId());
    }

    @Test
    void getStatus_shouldReturn200WithJobStatus() {
        PipelineJob job = PipelineJob.create();
        job.markProcessing();
        when(repository.findById(job.getId())).thenReturn(Optional.of(job));

        ResponseEntity<JobStatusResponse> response = controller.getStatus(job.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PROCESSING", response.getBody().status());
    }

    @Test
    void getStatus_shouldReturn404WhenJobNotFound() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());

        ResponseEntity<JobStatusResponse> response = controller.getStatus("nonexistent");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getResult_shouldReturn200WithDeserializedResult() throws Exception {
        PipelineJob job = PipelineJob.create();
        job.markProcessing();

        PipelineResult result = new PipelineResult(
                new IdeaContext("idea", List.of(), "summary"),
                new PipelineOutput("ctx", "flow", "task"),
                new ArchitectureDoc("Java 21", List.of(), List.of(), "Docker", "test"),
                new TaskGraph(List.of(), java.util.Map.of(), 0, java.util.Map.of()),
                0
        );
        String resultJson = objectMapper.writeValueAsString(result);
        job.markDone(resultJson);

        when(repository.findById(job.getId())).thenReturn(Optional.of(job));

        ResponseEntity<JobResultResponse> response = controller.getResult(job.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DONE", response.getBody().status());
    }

    @Test
    void getResult_shouldReturn409WhenJobNotDone() {
        PipelineJob job = PipelineJob.create();
        job.markProcessing();
        when(repository.findById(job.getId())).thenReturn(Optional.of(job));

        ResponseEntity<JobResultResponse> response = controller.getResult(job.getId());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void getResult_shouldReturn404WhenJobNotFound() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());

        ResponseEntity<JobResultResponse> response = controller.getResult("nonexistent");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getHealth_shouldReturn200OK() {
        ResponseEntity<String> response = controller.health();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("OK", response.getBody());
    }
}