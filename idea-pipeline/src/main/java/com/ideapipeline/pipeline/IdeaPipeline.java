package com.ideapipeline.pipeline;

import com.ideapipeline.config.PipelineProperties;
import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.*;
import com.ideapipeline.orchestrator.*;
import com.ideapipeline.parser.TaskParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class IdeaPipeline {

    private final GatekeeperOrchestrator gatekeeperOrchestrator;
    private final DebateOrchestrator debateOrchestrator;
    private final SynthesisOrchestrator synthesisOrchestrator;
    private final CriticOrchestrator criticOrchestrator;
    private final ValidationOrchestrator validationOrchestrator;
    private final StackArchitectOrchestrator stackArchitectOrchestrator;
    private final ScrumMasterOrchestrator scrumMasterOrchestrator;
    private final PipelineProperties pipelineProperties;

    public IdeaPipeline(
            GatekeeperOrchestrator gatekeeperOrchestrator,
            DebateOrchestrator debateOrchestrator,
            SynthesisOrchestrator synthesisOrchestrator,
            CriticOrchestrator criticOrchestrator,
            ValidationOrchestrator validationOrchestrator,
            StackArchitectOrchestrator stackArchitectOrchestrator,
            ScrumMasterOrchestrator scrumMasterOrchestrator,
            PipelineProperties pipelineProperties) {
        this.gatekeeperOrchestrator = gatekeeperOrchestrator;
        this.debateOrchestrator = debateOrchestrator;
        this.synthesisOrchestrator = synthesisOrchestrator;
        this.criticOrchestrator = criticOrchestrator;
        this.validationOrchestrator = validationOrchestrator;
        this.stackArchitectOrchestrator = stackArchitectOrchestrator;
        this.scrumMasterOrchestrator = scrumMasterOrchestrator;
        this.pipelineProperties = pipelineProperties;
    }

    public List<String> startPipeline(RawIdea idea) {
        log.info("--- STARTING PIPELINE (Initial Pass) ---");
        List<String> questions = gatekeeperOrchestrator.generateRefinementQuestions(idea);
        log.info("Generated {} initial questions.", questions.size());
        return questions;
    }

    public PipelineResult run(
            RawIdea rawIdea,
            List<RefinementQA> refinements,
            Team team) throws Exception {

        if (rawIdea == null || rawIdea.description() == null || rawIdea.description().isBlank()) {
            throw new PipelineException("rawIdea is null or empty", "Phase0", 0);
        }
        if (team == null) {
            throw new PipelineException("team is null", "Phase4", 0);
        }

        List<RefinementQA> safeRefinements = refinements == null ? Collections.emptyList() : refinements;
        log.info("Running pipeline for idea (length={}) with {} team members",
                rawIdea.description().length(), team.members().size());

        int debateRounds = pipelineProperties.getDefaultDebateRounds();
        int maxLoops = pipelineProperties.getMaxLoops();
        int convergenceThreshold = pipelineProperties.getConvergenceThreshold();

        log.info("Phase 0 — Context building");
        IdeaContext ideaContext = gatekeeperOrchestrator.buildContext(rawIdea, safeRefinements);

        log.info("Phase 1 — Debate ({} rounds)", debateRounds);
        List<DebateMessage> debateHistory = debateOrchestrator.runDebate(ideaContext, debateRounds);

        log.info("Phase 2 — Document synthesis");
        PipelineOutput pipelineOutput = synthesisOrchestrator.synthesize(ideaContext, debateHistory);

        int loopCount = 0;
        IdeaContext currentContext = ideaContext;
        PipelineOutput currentOutput = pipelineOutput;
        List<DebateMessage> currentHistory = debateHistory;

        while (true) {
            log.info("Phase 3a — Critique (loopCount={})", loopCount);
            CritiqueResult critiqueResult = criticOrchestrator.critique(currentOutput, currentHistory);

            log.info("Phase 3b — Validation (loopCount={})", loopCount);
            ValidationResult validationResult = validationOrchestrator.validate(currentOutput, critiqueResult, loopCount);

            log.info("convergenceScore={}, shouldLoop={}, loopCount={}",
                    validationResult.convergenceScore(), validationResult.shouldLoop(), loopCount);

            if (validationResult.convergenceScore() >= convergenceThreshold || !validationResult.shouldLoop()) {
                log.info("Pipeline converged at loopCount={}", loopCount);
                break;
            }

            if (loopCount + 1 >= maxLoops) {
                log.warn("Forcing convergence after {} loops", loopCount + 1);
                break;
            }

            log.info("Looping to refine — enriching context with gaps");
            currentContext = enrichContextWithGaps(currentContext, critiqueResult, loopCount);
            currentHistory = debateOrchestrator.runDebate(currentContext, debateRounds);
            currentOutput = synthesisOrchestrator.synthesize(currentContext, currentHistory);
            loopCount++;
        }

        log.info("Phase 3.5 — Stack architecture");
        ArchitectureDoc architectureDoc = stackArchitectOrchestrator.defineStack(currentContext, currentOutput, currentHistory);

        log.info("Phase 4 — Planning poker");
        List<Task> tasks = TaskParser.parse(currentOutput.taskDocument());
        TaskGraph taskGraph = scrumMasterOrchestrator.estimateTasks(tasks, architectureDoc, team);

        log.info("Pipeline complete: loopsRequired={}, totalPoints={}", loopCount, taskGraph.totalPoints());

        return new PipelineResult(currentContext, currentOutput, architectureDoc, taskGraph, loopCount);
    }

    private IdeaContext enrichContextWithGaps(IdeaContext context, CritiqueResult critique, int loopCount) {
        String gaps = String.join("; ", critique.gaps());
        String needs = String.join("; ", critique.refinementNeeds());

        String additionalContext = "\n--- Refinement round %d ---\nGaps: %s\nNeeds: %s"
                .formatted(loopCount + 1, gaps, needs);

        return new IdeaContext(
                context.rawIdea(),
                context.refinements(),
                context.enrichedSummary() + additionalContext
        );
    }
}