package com.ideapipeline.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.ideapipeline.client.JsonParser;
import com.ideapipeline.client.LlmClient;
import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.ArchitectureDoc;
import com.ideapipeline.model.DebateMessage;
import com.ideapipeline.model.IdeaContext;
import com.ideapipeline.model.PipelineOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class StackArchitectOrchestrator {

    private static final String STACK_ARCHITECT_SYSTEM_PROMPT = """
            You are a Software Architect senior who defines the technology stack
            for a software project based on converged design documents.
            You consider scalability, team complexity, time-to-market and costs.
            You justify every decision. You do not choose technology for hype.

            Analyze the idea context, the 3 documents and the debate history
            to produce a complete architecture specification.

            Respond ONLY in JSON with this exact format, no preamble, no markdown:
            {
              "stack": "Java 21, Spring Boot 3.3, PostgreSQL, Redis, Docker",
              "services": ["API Gateway", "Auth Service", "Core Service"],
              "constraints": ["Must support Java 21", "Containerized deployment"],
              "deploymentModel": "Cloud-native container orchestration (Kubernetes)",
              "rationale": "Detailed justification of all decisions..."
            }
            """;

    private final LlmClient llmClient;

    public StackArchitectOrchestrator(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public ArchitectureDoc defineStack(IdeaContext context, PipelineOutput documents, List<DebateMessage> debateHistory) {
        try {
            if (context == null) {
                throw new PipelineException("context is null", "Phase3.5", 0);
            }
            if (context.enrichedSummary() == null || context.enrichedSummary().isBlank()) {
                throw new PipelineException("enrichedSummary is empty", "Phase3.5", 0);
            }
            if (documents == null) {
                throw new PipelineException("documents is null", "Phase3.5", 0);
            }

            validateDocument(documents.contextDocument(), "contextDocument");
            validateDocument(documents.flowDocument(), "flowDocument");
            validateDocument(documents.taskDocument(), "taskDocument");

            List<DebateMessage> safeDebateHistory = debateHistory == null ? Collections.emptyList() : debateHistory;
            log.info(
                    "Starting stack architecture definition with summary length={}, document sizes (context={}, flow={}, task={}) and debate history size={}",
                    context.enrichedSummary().length(),
                    documents.contextDocument().length(),
                    documents.flowDocument().length(),
                    documents.taskDocument().length(),
                    safeDebateHistory.size()
            );

            String userMessage = buildUserMessage(context, documents, safeDebateHistory);
            String response = llmClient.chat(STACK_ARCHITECT_SYSTEM_PROMPT, userMessage);

            if (response == null || response.isBlank()) {
                throw new PipelineException("Empty response from stack architect LLM", "Phase3.5", 0);
            }

            JsonNode root;
            try {
                root = JsonParser.parse(response);
            } catch (RuntimeException e) {
                log.error("Failed to parse architecture JSON", e);
                throw new PipelineException("Failed to parse architecture JSON", "Phase3.5", 0, e);
            }

            String stack = root.has("stack") ? root.get("stack").asText() : "";
            List<String> services = extractArray(root, "services");
            List<String> constraints = extractArray(root, "constraints");
            String deploymentModel = root.has("deploymentModel") ? root.get("deploymentModel").asText() : "";
            String rationale = root.has("rationale") ? root.get("rationale").asText() : "";

            log.info(
                    "Stack architecture defined: stack={}, services={}, constraints={}",
                    stack,
                    services.size(),
                    constraints.size()
            );

            return new ArchitectureDoc(stack, services, constraints, deploymentModel, rationale);
        } catch (PipelineException e) {
            log.error("Stack architecture definition failed", e);
            throw e;
        }
    }

    private void validateDocument(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new PipelineException("document is empty: " + name, "Phase3.5", 0);
        }
    }

    private String buildUserMessage(IdeaContext context, PipelineOutput documents, List<DebateMessage> debateHistory) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== IDEA CONTEXT ===\n");
        sb.append(context.enrichedSummary()).append("\n\n");
        sb.append("=== CONTEXT DOCUMENT ===\n");
        sb.append(documents.contextDocument()).append("\n\n");
        sb.append("=== FLOW DOCUMENT ===\n");
        sb.append(documents.flowDocument()).append("\n\n");
        sb.append("=== TASK DOCUMENT ===\n");
        sb.append(documents.taskDocument()).append("\n");

        if (!debateHistory.isEmpty()) {
            sb.append("\n=== DEBATE HISTORY ===\n");
            for (DebateMessage message : debateHistory) {
                sb.append(message.agentName())
                        .append(": ")
                        .append(message.content())
                        .append("\n");
            }
        }

        return sb.toString();
    }

    private List<String> extractArray(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            result.add(item.asText());
        }
        return result;
    }
}
