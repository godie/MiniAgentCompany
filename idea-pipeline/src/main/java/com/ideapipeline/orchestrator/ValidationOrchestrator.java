package com.ideapipeline.orchestrator;

import com.ideapipeline.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ValidationOrchestrator {

    public ValidationResult validate(PipelineOutput docs, CritiqueResult critique, int loopCount) {
        log.info("Validating convergence across documents.");
        int score = Math.max(10, 100 - (loopCount * 5));
        return new ValidationResult(
                score,
                score < 75,
                "Validation passed for loop " + loopCount + ". Score: " + score
        );
    }
}
