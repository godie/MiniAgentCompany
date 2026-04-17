package com.ideapipeline.controller;

import com.ideapipeline.model.PipelineResult;
import com.ideapipeline.model.RawIdea;
import com.ideapipeline.model.RunRequest;
import com.ideapipeline.model.Team;
import com.ideapipeline.model.TeamMember;
import com.ideapipeline.model.enums.BaseRole;
import com.ideapipeline.model.enums.Seniority;
import com.ideapipeline.pipeline.IdeaPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.List;

@RestController
@RequestMapping("/pipeline")
@RequiredArgsConstructor
@Slf4j
@Validated
public class PipelineController {

    private final IdeaPipeline ideaPipeline;

    @PostMapping("/start")
    public ResponseEntity<List<String>> startPipeline(@RequestBody RawIdea idea) {
        log.info("Received request to start pipeline for idea: {}", idea.description());
        List<String> questions = ideaPipeline.startPipeline(idea);
        return ResponseEntity.ok(questions);
    }

    @PostMapping("/run")
    public ResponseEntity<PipelineResult> runPipeline(@RequestBody RunRequest request) {
        log.info("Received request to run full pipeline: {}", request.triggerDescription());
        
        try {
            TeamMember dummyMember = new TeamMember(
                    "dummy-id",
                    "Dummy Member",
                    BaseRole.FULLSTACK_DEV,
                    Seniority.MID,
                    List.of()
            );
            PipelineResult result = ideaPipeline.runFullPipeline(
                    request.idea(),
                    List.of(),
                    new Team(List.of(dummyMember)),
                    request.debateRounds()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Pipeline execution failed.", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("{\"status\": \"ok\", \"version\": \"1.0\"}");
    }
}
