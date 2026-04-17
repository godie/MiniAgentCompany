package com.ideapipeline.orchestrator;

import com.ideapipeline.client.LlmClient;
import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.DebateMessage;
import com.ideapipeline.model.IdeaContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;

@Service
@Slf4j
public class DebateOrchestrator {

    private static final String TURN_PROMPT_TEMPLATE = """
            The topic is: "%s"
            This is round %d of the debate.
            Respond to the most recent points as %s.
            Be concise — max 3 sentences.
            """;

    private static final List<DebateAgent> AGENTS = List.of(
            new DebateAgent("ProductAgent", """
                    You are a senior Product Manager focused on user value, business model,
                    and market fit. Ask about the 'for whom' and the 'why'.
                    Reference other agents by name. Max 3 sentences per turn.
                    Be concrete and specific.
                    """),
            new DebateAgent("ArchitectAgent", """
                    You are a Software Architect focused on technical feasibility, stack,
                    data model, integrations and scalability.
                    Question unnecessary complexity.
                    Reference other agents by name. Max 3 sentences per turn.
                    Be concrete and specific.
                    """),
            new DebateAgent("CriticAgent", """
                    You are a devil's advocate. Find risks, unvalidated assumptions,
                    legal issues, adoption problems and monetization gaps.
                    You are not negative — you are rigorous.
                    Reference other agents by name. Max 3 sentences per turn.
                    Be concrete and specific.
                    """)
    );

    private final LlmClient llmClient;

    public DebateOrchestrator(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * Run N rounds of debate.
     *
     * @param context The idea context containing the raw idea and enriched summary
     * @param rounds The number of debate rounds to run (must be > 0)
     * @return List of DebateMessage from System and all agents across all rounds
     * @throws Exception if input is invalid, LLM fails, or debate is interrupted
     */
    public List<DebateMessage> runDebate(IdeaContext context, int rounds) throws Exception {
        if (rounds <= 0) {
            throw new PipelineException("rounds must be > 0", "Phase1", 0);
        }

        List<DebateMessage> history = new ArrayList<>();
        history.add(new DebateMessage("System", context.enrichedSummary(), 0));

        for (int round = 1; round <= rounds; round++) {
            log.info("Starting round {} of {}", round, rounds);
            List<DebateMessage> snapshot = List.copyOf(history);
            final int roundNumber = round;

            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                List<StructuredTaskScope.Subtask<DebateMessage>> subtasks = new ArrayList<>();
                for (DebateAgent agent : AGENTS) {
                    subtasks.add(scope.fork(() -> {
                        String response = llmClient.chatWithHistory(
                                agent.systemPrompt(),
                                snapshot,
                                TURN_PROMPT_TEMPLATE.formatted(
                                        context.rawIdea(),
                                        roundNumber,
                                        agent.name()
                                )
                        );

                        if (response == null || response.isBlank()) {
                            log.warn("Empty response from {}", agent.name());
                            response = "[No response]";
                        }

                        return new DebateMessage(agent.name(), response, roundNumber);
                    }));
                }

                scope.join().throwIfFailed();
                List<DebateMessage> messages = subtasks.stream()
                        .map(StructuredTaskScope.Subtask::get)
                        .toList();
                history.addAll(messages);
                log.info("Completed round {}, received {} responses", round, messages.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PipelineException("Debate interrupted", "Phase1", 0, e);
            } catch (Exception e) {
                throw new PipelineException("Debate failed", "Phase1", 0, e);
            }
        }

        return history;
    }

    private record DebateAgent(String name, String systemPrompt) {}
}
