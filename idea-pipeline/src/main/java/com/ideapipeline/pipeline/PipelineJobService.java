package com.ideapipeline.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ideapipeline.model.PipelineJob;
import com.ideapipeline.model.PipelineResult;
import com.ideapipeline.model.RawIdea;
import com.ideapipeline.model.RefinementQA;
import com.ideapipeline.model.Team;
import com.ideapipeline.repository.PipelineJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class PipelineJobService {

    private final IdeaPipeline ideaPipeline;
    private final PipelineJobRepository repository;
    private final ObjectMapper objectMapper;

    public PipelineJobService(IdeaPipeline ideaPipeline, PipelineJobRepository repository, ObjectMapper objectMapper) {
        this.ideaPipeline = ideaPipeline;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public String submitJob(RawIdea rawIdea, List<RefinementQA> refinements, Team team) {
        PipelineJob job = PipelineJob.create();
        repository.save(job);
        log.info("Job {} submitted", job.getId());
        executeAsync(job.getId(), rawIdea, refinements, team);
        return job.getId();
    }

    @Async
    public void executeAsync(String jobId, RawIdea rawIdea, List<RefinementQA> refinements, Team team) {
        PipelineJob job = repository.findById(jobId).orElseThrow();
        job.markProcessing();
        repository.save(job);
        log.info("Job {} processing", jobId);

        try {
            List<RefinementQA> safeRefinements = refinements != null ? refinements : Collections.emptyList();
            PipelineResult result = ideaPipeline.run(rawIdea, safeRefinements, team);
            String resultJson = objectMapper.writeValueAsString(result);
            job.markDone(resultJson);
            repository.save(job);
            log.info("Job {} done", jobId);
        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage());
            job.markError(e.getMessage());
            repository.save(job);
        }
    }
}