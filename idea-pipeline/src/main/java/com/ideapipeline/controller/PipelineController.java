package com.ideapipeline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ideapipeline.controller.dto.*;
import com.ideapipeline.model.*;
import com.ideapipeline.model.enums.PipelineJobStatus;
import com.ideapipeline.orchestrator.GatekeeperOrchestrator;
import com.ideapipeline.pipeline.PipelineJobService;
import com.ideapipeline.repository.PipelineJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/pipeline")
@Slf4j
public class PipelineController {

    private final PipelineJobService pipelineJobService;
    private final PipelineJobRepository repository;
    private final GatekeeperOrchestrator gatekeeperOrchestrator;
    private final ObjectMapper objectMapper;

    public PipelineController(PipelineJobService pipelineJobService,
                               PipelineJobRepository repository,
                               GatekeeperOrchestrator gatekeeperOrchestrator,
                               ObjectMapper objectMapper) {
        this.pipelineJobService = pipelineJobService;
        this.repository = repository;
        this.gatekeeperOrchestrator = gatekeeperOrchestrator;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/questions")
    public ResponseEntity<QuestionsResponse> postQuestions(@RequestBody QuestionsRequest request) {
        log.info("POST /pipeline/questions");
        if (request.description() == null || request.description().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        List<String> questions = gatekeeperOrchestrator.generateRefinementQuestions(new RawIdea(request.description()));
        return ResponseEntity.ok(new QuestionsResponse(questions));
    }

    @PostMapping("/run")
    public ResponseEntity<RunPipelineResponse> postRun(@RequestBody RunPipelineRequest request) {
        log.info("POST /pipeline/run");
        if (request.idea() == null || request.idea().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.team() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (request.team().members() == null || request.team().members().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        RawIdea rawIdea = new RawIdea(request.idea());
        List<RefinementQA> refinements = request.refinements() != null ? request.refinements() : Collections.emptyList();

        String jobId = pipelineJobService.submitJob(rawIdea, refinements, request.team());
        return ResponseEntity.accepted().body(new RunPipelineResponse(jobId, "QUEUED", "Pipeline job submitted"));
    }

    @GetMapping("/{jobId}/status")
    public ResponseEntity<JobStatusResponse> getStatus(@PathVariable String jobId) {
        log.info("GET /pipeline/{}/status", jobId);
        return repository.findById(jobId)
                .map(job -> ResponseEntity.ok(new JobStatusResponse(
                        job.getId(),
                        job.getStatus().name(),
                        job.getCreatedAt(),
                        job.getUpdatedAt(),
                        job.getErrorMessage()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{jobId}/result")
    public ResponseEntity<JobResultResponse> getResult(@PathVariable String jobId) {
        log.info("GET /pipeline/{}/result", jobId);
        var optJob = repository.findById(jobId);
        if (optJob.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PipelineJob job = optJob.get();
        if (job.getStatus() != PipelineJobStatus.DONE) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        try {
            PipelineResult result = objectMapper.readValue(job.getResultJson(), PipelineResult.class);
            return ResponseEntity.ok(new JobResultResponse(job.getId(), job.getStatus().name(), result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}