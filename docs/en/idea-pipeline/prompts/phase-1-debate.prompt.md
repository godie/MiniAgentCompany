# Phase 1: Debate Prompt

## Objective
Create a structured debate framework for evaluating ideas through adversarial questioning and constructive critique.

---

# phase-1-debate.prompt.md v1.1

## Role
You are a Senior Java Developer implementing Phase 1 of the idea-pipeline system.
Phase 1 runs a multi-agent debate where 3 agents discuss an idea in parallel
using Java 21 virtual threads and StructuredTaskScope.

## Context
Read these files before writing any code:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

---

## What already exists — do not modify

These files are FULLY IMPLEMENTED and tested:

- LlmClient.java — interface:
 - chat(String systemPrompt, String userMessage)
 - chatWithHistory(String systemPrompt, List<DebateMessage> history, String userMessage)

- LlmClientImpl.java — @Component, fully implemented. 6 tests passing.

- JsonParser.java — package com.ideapipeline.client
 public static JsonNode parse(String response)

- GatekeeperOrchestrator.java — fully implemented. 8 tests passing.

- IdeaContext.java, DebateMessage.java — records, do not modify

- PipelineException.java — constructors:
 - PipelineException(String message, String phase, int loopCount)
 - PipelineException(String message, String phase, int loopCount, Throwable cause)

- DebateOrchestrator.java — YOUR TARGET FILE
 Currently throws UnsupportedOperationException.
 Inject LlmClient via constructor.

---

## Project status
- mvn clean compile → SUCCESS
- mvn test → 14/14 passing

---

## Your task

Implement DebateOrchestrator.java — one method: runDebate()

---

## The 3 agents — define as private static final records

```java
private record DebateAgent(String name, String systemPrompt) {}

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
```

---

Turn prompt template (REQUIRED CONSTANT)

```java
private static final String TURN_PROMPT_TEMPLATE = """
The topic is: "%s"
This is round %d of the debate.
Respond to the most recent points as %s.
Be concise — max 3 sentences.
""";
```

---

## Implementation: DebateOrchestrator.java

### Class annotations

```java
@Service
@Slf4j
public class DebateOrchestrator {
    
    private final LlmClient llmClient;
    
    public DebateOrchestrator(LlmClient llmClient) {
        this.llmClient = llmClient;
    }
    
    // ... rest of implementation
}
```

---

## Method: runDebate()

Signature:

```java
public List<DebateMessage> runDebate(IdeaContext context, int rounds) throws Exception
```

---

## Purpose

Run N rounds of debate.

Within each round:
- All 3 agents execute IN PARALLEL
- Each agent receives a SNAPSHOT of the history BEFORE the round
- Agents in the same round DO NOT see each other's responses

---

## Complete Implementation

```java
public List<DebateMessage> runDebate(IdeaContext context, int rounds) throws Exception {
    // 1. Validate input
    if (rounds <= 0) {
        throw new PipelineException("rounds must be > 0", "Phase1", 0);
    }

    // 2. Initialize history
    List<DebateMessage> history = new ArrayList<>();
    history.add(new DebateMessage("System", context.enrichedSummary(), 0));

    // 3. Execute rounds
    for (int round = 1; round <= rounds; round++) {
        
        // a. Log start
        log.info("Starting round {} of {}", round, rounds);

        // b. Snapshot - IMMUTABLE COPY
        List<DebateMessage> snapshot = List.copyOf(history);

        // c. Parallel execution with StructuredTaskScope.ShutdownOnFailure
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<StructuredTaskScope.Subtask<DebateMessage>> subtasks = AGENTS.stream()
                .map(agent -> scope.fork(() -> {
                    
                    String response = llmClient.chatWithHistory(
                        agent.systemPrompt(),
                        snapshot,
                        TURN_PROMPT_TEMPLATE.formatted(
                            context.rawIdea(),
                            round,
                            agent.name()
                        )
                    );

                    if (response == null || response.isBlank()) {
                        log.warn("Empty response from {}", agent.name());
                        response = "[No response]";
                    }

                    return new DebateMessage(agent.name(), response, round);
                }));

            List<DebateMessage> messages = scope.join().throwIfFailed();

            // Add all responses to history
            history.addAll(messages);

            log.info("Completed round {}, received {} responses", round, messages.size());
        }

        // 4. Exception handling is handled by ShutdownOnFailure automatically
        // but explicit handling for InterruptedException:
        // (included in the try block above)
    }

    // 5. Return result
    return history;
}
```

---

## Coding standards

- Constructor injection only (LlmClient)
- Annotate class with @Service and @Slf4j
- No field injection
- Use StructuredTaskScope.ShutdownOnFailure
- Snapshot MUST be List.copyOf()
- No hardcoded strings outside constants
- Handle null/blank LLM responses defensively
- Always wrap errors in PipelineException

---

## Testing requirements (TDD)

Test file: `src/test/java/com/ideapipeline/orchestrator/DebateOrchestratorTest.java`

Use:

```java
@ExtendWith(MockitoExtension.class)
@Mock LlmClient llmClient;
```

---

### Required tests

1. `runDebate_shouldReturnHistoryWithSeedAndAllRounds`
2. `runDebate_shouldSetCorrectRoundNumberOnMessages`
3. `runDebate_shouldSetCorrectAgentNames`
4. `runDebate_shouldThrowPipelineException_onInvalidRounds`
5. `runDebate_shouldThrowPipelineException_onLlmFailure`
6. `runDebate_shouldIncludeSeedMessageInHistory`
7. `runDebate_shouldHandleSingleRound`

---

### Additional REQUIRED test (snapshot correctness)

**`runDebate_shouldPassImmutableSnapshotToAgents`**

Use `ArgumentCaptor<List<DebateMessage>>`

Verify:
- Lists passed to `chatWithHistory()` are NOT the same instance as history
- Lists are not mutated across rounds

---

### Additional REQUIRED test (empty response handling)

**`runDebate_shouldHandleEmptyResponse`**

Mock:
```java
when(llmClient.chatWithHistory(...)).thenReturn("");
```

Verify:
- Message content is "[No response]"

---

## Acceptance criteria

1. `mvn clean compile → SUCCESS`
2. `mvn test → +9 tests pass (DebateOrchestratorTest)`
3. Total tests ≥ 23 passing
4. No UnsupportedOperationException
5. Parallelism uses StructuredTaskScope.ShutdownOnFailure
6. Snapshot uses List.copyOf()
7. InterruptedException preserves interrupt flag
8. TURN_PROMPT_TEMPLATE defined as constant
9. Empty/null responses handled safely
10. Logs present for each round

---

End of phase-1-debate.prompt.md v1.1
