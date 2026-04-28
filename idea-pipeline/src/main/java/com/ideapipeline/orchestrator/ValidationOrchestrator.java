package com.ideapipeline.orchestrator;

import com.ideapipeline.client.JsonParser;
import com.ideapipeline.client.LlmClient;
import com.ideapipeline.config.PipelineProperties;
import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ValidationOrchestrator {

    private static final String VALIDATION_SYSTEM_PROMPT = """
        You are a convergence evaluator for a multi-agent pipeline.
        You receive 3 documents and a critique report and must decide
        if the pipeline has converged or needs another iteration.

        convergenceScore rules:
        - 0-40: major inconsistencies, loop mandatory
        - 41-74: significant gaps, loop recommended
        - 75-100: documents are coherent, advance

        Respond ONLY in JSON with this exact format, no preamble, no markdown:
        {
          "convergenceScore": 85,
          "shouldLoop": false,
          "reasoning": "explanation"
        }
        """;

    private final LlmClient llmClient;
    private final PipelineProperties pipelineProperties;

    public ValidationOrchestrator(LlmClient llmClient, PipelineProperties pipelineProperties) {
        this.llmClient = llmClient;
        this.pipelineProperties = pipelineProperties;
    }

    public ValidationResult validate(PipelineOutput docs, CritiqueResult critique, int loopCount) {
        if (docs == null) {
            throw new PipelineException("docs is null", "Phase3b", 0);
        }
        if (critique == null) {
            throw new PipelineException("critique is null", "Phase3b", loopCount);
        }
        if (loopCount < 0) {
            throw new PipelineException("loopCount must be >= 0", "Phase3b", loopCount);
        }

        log.info("Starting validation for loopCount={}, critiques={}, gaps={}, refinementNeeds={}",
                loopCount,
                critique.critiques() != null ? critique.critiques().size() : 0,
                critique.gaps() != null ? critique.gaps().size() : 0,
                critique.refinementNeeds() != null ? critique.refinementNeeds().size() : 0);

        String critiquesSection = formatList(critique.critiques());
        String gapsSection = formatList(critique.gaps());
        String refinementSection = formatList(critique.refinementNeeds());

        String userMessage = String.format("""
            === DOCUMENTS ===
            --- CONTEXT ---
            %s
            --- FLOWS ---
            %s
            --- TASKS ---
            %s
            === CRITIQUE (iteration %d) ===
            Critiques:
            %s

            Gaps:
            %s

            Refinement needs:
            %s
            """,
                docs.contextDocument(),
                docs.flowDocument(),
                docs.taskDocument(),
                loopCount,
                critiquesSection,
                gapsSection,
                refinementSection
        );

        String response = llmClient.chat(VALIDATION_SYSTEM_PROMPT, userMessage);

        if (response == null || response.isBlank()) {
            throw new PipelineException("Empty response from validation LLM", "Phase3b", loopCount);
        }

        JsonNode jsonNode;
        try {
            jsonNode = JsonParser.parse(response);
        } catch (RuntimeException e) {
            log.error("Failed to parse validation JSON", e);
            throw new PipelineException("Failed to parse validation JSON", "Phase3b", loopCount, e);
        }

        int convergenceScore = jsonNode.has("convergenceScore") ? jsonNode.get("convergenceScore").asInt() : 0;
        boolean shouldLoop = jsonNode.has("shouldLoop") ? jsonNode.get("shouldLoop").asBoolean() : true;
        String reasoning = jsonNode.has("reasoning") ? jsonNode.get("reasoning").asText() : "";

        log.info("Parsed validation result: convergenceScore={}, shouldLoop={}", convergenceScore, shouldLoop);

        if (convergenceScore < pipelineProperties.getConvergenceThreshold()) {
            log.warn("Convergence score {} is below threshold {}",
                    convergenceScore, pipelineProperties.getConvergenceThreshold());
        }

        return new ValidationResult(convergenceScore, shouldLoop, reasoning);
    }

    private String formatList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "- (none)";
        }
        return items.stream()
                .map(item -> "- " + item)
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
