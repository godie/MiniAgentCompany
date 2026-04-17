package com.ideapipeline.orchestrator;

import com.ideapipeline.client.LlmClient;
import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.StructuredTaskScope;

@Service
@Slf4j
public class SynthesisOrchestrator {

    private final LlmClient llmClient;

    private static final String CONTEXT_DOC_PROMPT = """
You are a technical writer. Based on the idea context and debate history,
generate a context document in Markdown with these sections:
## Vision
## Problem
## Target Users
## Scope
## Constraints
## Assumptions
## Glossary
Be precise and concise. Only include what was discussed.
Respond in Markdown only, no preamble.
""";

    private static final String FLOW_DOC_PROMPT = """
You are a business analyst. Based on the idea context and debate history,
generate a flow document in Markdown with these sections:
## Main User Flows (happy path)
## Alternative Flows
## Edge Cases
## Mermaid Diagrams (at least one)
Be precise and concise. Only include what was discussed.
Respond in Markdown only, no preamble.
""";

    private static final String TASK_DOC_PROMPT = """
You are a tech lead. Based on the idea context and debate history,
generate a task document in Markdown with this structure:
## Epic 1: [name]
### User Story: [story]
#### Task: [task title]
- Description: ...
- Acceptance criteria: ...
- Estimate: S | M | L
Prioritize by value and dependencies.
Respond in Markdown only, no preamble.
""";

    public SynthesisOrchestrator(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public PipelineOutput synthesize(IdeaContext context, List<DebateMessage> debateHistory) throws Exception {
        int historySize = debateHistory != null ? debateHistory.size() : 0;
        log.info("Starting synthesis with debate history size: {}", historySize);

        if (context == null) {
            throw new PipelineException("context is null", "Phase2", 0);
        }
        if (debateHistory == null) {
            throw new PipelineException("debateHistory is null", "Phase2", 0);
        }

        if (debateHistory.isEmpty()) {
            log.warn("No debate history — synthesizing from context only");
        }

        String userMessage = buildUserMessage(context, debateHistory);

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var contextTask = scope.fork(() -> llmClient.chat(CONTEXT_DOC_PROMPT, userMessage));
            var flowTask = scope.fork(() -> llmClient.chat(FLOW_DOC_PROMPT, userMessage));
            var taskDocTask = scope.fork(() -> llmClient.chat(TASK_DOC_PROMPT, userMessage));

            scope.join();
            scope.throwIfFailed();

            String contextDocument = contextTask.get();
            String flowDocument = flowTask.get();
            String taskDocument = taskDocTask.get();

            validateDocument(contextDocument, "Context document");
            validateDocument(flowDocument, "Flow document");
            validateDocument(taskDocument, "Task document");

            log.info("Successfully generated 3 documents: context, flow, task");
            return new PipelineOutput(contextDocument, flowDocument, taskDocument);

        } catch (PipelineException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PipelineException("Synthesis interrupted", "Phase2", 0, e);
        } catch (Exception e) {
            log.error("Synthesis failed", e);
            throw new PipelineException("Synthesis failed", "Phase2", 0, e);
        }
    }

    private String buildUserMessage(IdeaContext context, List<DebateMessage> debateHistory) {
        StringBuilder sb = new StringBuilder();
        sb.append("Idea summary: ").append(context.enrichedSummary()).append("\n\n");

        if (debateHistory.isEmpty()) {
            sb.append("No debate history available.");
        } else {
            sb.append("Debate history:\n");
            for (DebateMessage msg : debateHistory) {
                sb.append(msg.agentName()).append(": ").append(msg.content()).append("\n");
            }
        }

        return sb.toString();
    }

    private void validateDocument(String document, String docName) {
        if (document == null || document.isBlank()) {
            throw new PipelineException("Empty document from synthesizer: " + docName, "Phase2", 0);
        }
    }
}
