package com.ideapipeline.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.*;
import com.ideapipeline.model.enums.BaseRole;
import com.ideapipeline.model.enums.PipelineJobStatus;
import com.ideapipeline.model.enums.Seniority;
import com.ideapipeline.repository.PipelineJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineJobServiceTest {

    @Mock
    private IdeaPipeline ideaPipeline;

    @Mock
    private PipelineJobRepository repository;

    @Mock
    private ObjectMapper objectMapper;

    @Spy
    @InjectMocks
    private PipelineJobService pipelineJobService;

    private static final RawIdea VALID_IDEA = new RawIdea("test idea");
    private static final Team VALID_TEAM = new Team(List.of(
            new TeamMember("tm-1", "Alice", BaseRole.BACKEND_DEV, Seniority.SENIOR, List.of())
    ));
    private static final PipelineResult VALID_RESULT = new PipelineResult(
            new IdeaContext("idea", List.of(), "summary"),
            new PipelineOutput("ctx", "flow", "task"),
            new ArchitectureDoc("Java 21", List.of(), List.of(), "Docker", "test"),
            new TaskGraph(List.of(), java.util.Map.of(), 0, java.util.Map.of()),
            0
    );

    @Test
    void submitJob_shouldSaveJobAndReturnJobId() throws Exception {
        when(repository.save(any(PipelineJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(pipelineJobService).executeAsync(any(), any(), any(), any());

        String jobId = pipelineJobService.submitJob(VALID_IDEA, List.of(), VALID_TEAM);

        assertNotNull(jobId);
        verify(repository).save(any(PipelineJob.class));
        verify(pipelineJobService).executeAsync(eq(jobId), any(), any(), any());
    }

    @Test
    void submitJob_shouldCallExecuteAsync() throws Exception {
        when(repository.save(any(PipelineJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(pipelineJobService).executeAsync(any(), any(), any(), any());

        String jobId = pipelineJobService.submitJob(VALID_IDEA, List.of(), VALID_TEAM);

        verify(pipelineJobService).executeAsync(eq(jobId), any(), any(), any());
    }

    @Test
    void executeAsync_shouldMarkProcessingThenDone() throws Exception {
        PipelineJob job = PipelineJob.create();
        when(repository.findById(job.getId())).thenReturn(Optional.of(job));
        when(ideaPipeline.run(any(), any(), any())).thenReturn(VALID_RESULT);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        pipelineJobService.executeAsync(job.getId(), VALID_IDEA, List.of(), VALID_TEAM);

        assertEquals(PipelineJobStatus.DONE, job.getStatus());
        assertNotNull(job.getResultJson());
        verify(repository, times(2)).save(any(PipelineJob.class));
    }

    @Test
    void executeAsync_shouldMarkErrorOnException() throws Exception {
        PipelineJob job = PipelineJob.create();
        when(repository.findById(job.getId())).thenReturn(Optional.of(job));
        when(ideaPipeline.run(any(), any(), any()))
                .thenThrow(new PipelineException("failed", "Phase1", 0));

        pipelineJobService.executeAsync(job.getId(), VALID_IDEA, List.of(), VALID_TEAM);

        assertEquals(PipelineJobStatus.ERROR, job.getStatus());
        assertNotNull(job.getErrorMessage());
        verify(repository, times(2)).save(any(PipelineJob.class));
    }

    @Test
    void executeAsync_shouldTruncateErrorMessageOver2000Chars() throws Exception {
        PipelineJob job = PipelineJob.create();
        String longMessage = "x".repeat(2500);

        when(repository.findById(job.getId())).thenReturn(Optional.of(job));
        when(ideaPipeline.run(any(), any(), any()))
                .thenThrow(new PipelineException(longMessage, "Phase1", 0));

        pipelineJobService.executeAsync(job.getId(), VALID_IDEA, List.of(), VALID_TEAM);

        assertEquals(PipelineJobStatus.ERROR, job.getStatus());
        assertTrue(job.getErrorMessage().length() <= 2000);
    }
}