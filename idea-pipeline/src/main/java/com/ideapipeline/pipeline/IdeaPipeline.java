package com.ideapipeline.pipeline;

import com.ideapipeline.config.PipelineProperties;
import com.ideapipeline.model.*;
import com.ideapipeline.orchestrator.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
    private final PlanningPokerOrchestrator planningPokerOrchestrator;
    private final PipelineProperties pipelineProperties;

    public IdeaPipeline(
            GatekeeperOrchestrator gatekeeperOrchestrator,
            DebateOrchestrator debateOrchestrator,
            SynthesisOrchestrator synthesisOrchestrator,
            CriticOrchestrator criticOrchestrator,
            ValidationOrchestrator validationOrchestrator,
            StackArchitectOrchestrator stackArchitectOrchestrator,
            PlanningPokerOrchestrator planningPokerOrchestrator,
            PipelineProperties pipelineProperties) {
        this.gatekeeperOrchestrator = gatekeeperOrchestrator;
        this.debateOrchestrator = debateOrchestrator;
        this.synthesisOrchestrator = synthesisOrchestrator;
        this.criticOrchestrator = criticOrchestrator;
        this.validationOrchestrator = validationOrchestrator;
        this.stackArchitectOrchestrator = stackArchitectOrchestrator;
        this.planningPokerOrchestrator = planningPokerOrchestrator;
        this.pipelineProperties = pipelineProperties;
    }

    public List<String> startPipeline(RawIdea idea) {
        log.info("--- STARTING PIPELINE (Initial Pass) ---");
        List<String> questions = gatekeeperOrchestrator.generateRefinementQuestions(idea);
        log.info("Generated {} initial questions.", questions.size());
        return questions;
    }

    public PipelineResult runFullPipeline(
            RawIdea idea,
            List<RefinementQA> answers,
            Team team,
            int debateRounds) throws Exception {

        int rounds = debateRounds <= 0 ? pipelineProperties.getDefaultDebateRounds() : debateRounds;

        IdeaContext context = gatekeeperOrchestrator.buildContext(idea, answers);
        List<DebateMessage> debateHistory = debateOrchestrator.runDebate(context, rounds);
        PipelineOutput documents = synthesisOrchestrator.synthesize(context, debateHistory);

        int loopCount = 0;
        int maxLoops = pipelineProperties.getMaxLoops();
        IdeaContext currentContext = context;
        PipelineOutput currentDocs = documents;
        List<DebateMessage> currentHistory = debateHistory;
        CritiqueResult lastCritique = null;

        while (true) {
            if (loopCount > 0) {
                 log.info("--- STARTING PIPELINE LOOP {} ---", loopCount);
            }

            try {
                CritiqueResult critique = criticOrchestrator.critique(currentDocs, currentHistory);
                lastCritique = critique;
                ValidationResult validation = validationOrchestrator.validate(currentDocs, critique, loopCount);

                if (validation.shouldLoop() && loopCount < maxLoops) {
                    log.warn("Convergence not met. Looping to refinement phase.");
                    
                    currentContext = enrichContextWithGaps(currentContext, critique);
                    currentHistory = debateOrchestrator.runDebate(currentContext, rounds);
                    currentDocs = synthesisOrchestrator.synthesize(currentContext, currentHistory);
                    
                    loopCount++;

                } else {
                    log.info("Pipeline converged or max loops reached. Finalizing.");
                    break;
                }
            } catch (UnsupportedOperationException e) {
                log.warn("Caught expected placeholder exception during loop: {}. Breaking loop.", e.getMessage());
                break;
            }
        }

        ArchitectureDoc architecture = stackArchitectOrchestrator.defineStack(currentContext, currentDocs, currentHistory);
        TaskGraph taskGraph = planningPokerOrchestrator.runPlanningPoker(currentDocs.taskDocument(), team, architecture);


        return new PipelineResult(
                currentContext,
                currentDocs,
                architecture,
                taskGraph,
                loopCount
        );
    }

    private IdeaContext enrichContextWithGaps(IdeaContext context, CritiqueResult critique) {
        String additionalContext = """
        GAPS FROM PREVIOUS ITERATION:
        %s

        TOPICS TO RE-DEBATE:
        %s
        """.formatted(
            String.join("\n- ", critique.gaps()),
            String.join("\n- ", critique.refinementNeeds())
        );

        return new IdeaContext(
            context.rawIdea(),
            context.refinements(),
            context.enrichedSummary() + "\n\n" + additionalContext
        );
    }
}
