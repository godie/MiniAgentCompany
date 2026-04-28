# phase-3.5-stack-architect.prompt.md v1.0

## Role
You are a Senior Java Developer implementing Phase 3.5 of the idea-pipeline system.
Phase 3.5 receives the converged IdeaContext, the 3 generated documents and the
debate history, and produces an `ArchitectureDoc` that defines the complete
technology stack, services, constraints, deployment model and rationale.

## Context
Read these files before writing any code:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

---

## What already exists — do not modify

- `LlmClient.java` — interface, fully implemented. 6 tests passing.
- `LlmClientImpl.java` — @Component, fully implemented.
- `JsonParser.java` — `public static JsonNode parse(String response)`
- `GatekeeperOrchestrator.java` — fully implemented. 8 tests passing.
- `DebateOrchestrator.java` — fully implemented. 7 tests passing.
- `SynthesisOrchestrator.java` — fully implemented. 8 tests passing.
- `CriticOrchestrator.java` — fully implemented. 11 tests passing.
- `ValidationOrchestrator.java` — fully implemented. 11 tests passing.
- `IdeaContext.java`, `PipelineOutput.java`, `DebateMessage.java` — records, do not modify
- `ArchitectureDoc.java` — record, do not modify:
  - `ArchitectureDoc(String stack, List<String> services, List<String> constraints, String deploymentModel, String rationale)`
- `PipelineException.java` — constructors:
  - `PipelineException(String message, String phase, int loopCount)`
  - `PipelineException(String message, String phase, int loopCount, Throwable cause)`
- `StackArchitectOrchestrator.java` — YOUR TARGET FILE
  Currently returns hardcoded placeholder values.
  Inject `LlmClient` via constructor.

## Project status
- mvn clean compile → SUCCESS
- mvn test → 51/51 passing

---

## Your task

Implement `StackArchitectOrchestrator.java` — one method: `defineStack()`

---

## System prompt — define as private static final String constant

```java
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
```

---

## Method: defineStack()

**Signature:**
```java
public ArchitectureDoc defineStack(
    IdeaContext context,
    PipelineOutput documents,
    List<DebateMessage> debateHistory
)
```

**Purpose:** Send the idea context, 3 converged documents and the debate
history to the LLM architect and parse the structured JSON response into
an `ArchitectureDoc`.

**Build user message — in this exact order:**

```
=== IDEA CONTEXT ===
[context.enrichedSummary()]

=== CONTEXT DOCUMENT ===
[documents.contextDocument()]

=== FLOW DOCUMENT ===
[documents.flowDocument()]

=== TASK DOCUMENT ===
[documents.taskDocument()]

=== DEBATE HISTORY ===
[agentName]: content
[agentName]: content
...
```

If `debateHistory` is null or empty, omit the `=== DEBATE HISTORY ===` section entirely.

Each debate message is formatted as: `[message.agentName()]: [message.content()]`, one per line.

**Implementation steps:**

1. Validate:
   - If `context` is null: throw `PipelineException("context is null", "Phase3.5", 0)`
   - If `context.enrichedSummary()` is null or blank: throw `PipelineException("enrichedSummary is empty", "Phase3.5", 0)`
   - If `documents` is null: throw `PipelineException("documents is null", "Phase3.5", 0)`
   - If any document is null or blank: throw `PipelineException("document is empty: [name]", "Phase3.5", 0)`
   - If `debateHistory` is null: treat as empty list (no exception — history is optional context)

2. Build `userMessage`:
   - Always include the IDEA CONTEXT, CONTEXT DOCUMENT, FLOW DOCUMENT and TASK DOCUMENT sections
   - If `debateHistory` is not empty, append the `=== DEBATE HISTORY ===` section with each message formatted as `[agentName]: content`

3. Call `llmClient.chat(STACK_ARCHITECT_SYSTEM_PROMPT, userMessage)`

4. If response is null or blank:
   throw `PipelineException("Empty response from stack architect LLM", "Phase3.5", 0)`

5. Parse with `JsonParser.parse(response)` — wrap RuntimeException:
   throw `PipelineException("Failed to parse architecture JSON", "Phase3.5", 0, e)`

6. Extract from JsonNode:
   - `stack` → String (default `""` if missing)
   - `services` → `List<String>` (default `Collections.emptyList()` if missing or not array)
   - `constraints` → `List<String>` (default `Collections.emptyList()` if missing or not array)
   - `deploymentModel` → String (default `""` if missing)
   - `rationale` → String (default `""` if missing)

7. Return `new ArchitectureDoc(stack, services, constraints, deploymentModel, rationale)`

**Logging:**
- `log.info()` on entry: enrichedSummary length + document sizes + debate history message count
- `log.info()` after parsing: stack value + number of services and constraints
- `log.error()` on any caught exception before rethrowing

---

## Coding standards
- Constructor injection only
- @Service + @Slf4j
- No @Autowired on fields
- All JSON parsing via `JsonParser.parse()`
- System prompt as private static final String constant
- Missing JSON fields use safe defaults (never null, never exception)
- Missing JSON arrays default to `Collections.emptyList()`
- `debateHistory` is included in `userMessage` when non-empty, omitted when null or empty

---

## Testing requirements (TDD)

Test file: `src/test/java/com/ideapipeline/orchestrator/StackArchitectOrchestratorTest.java`
Use `@ExtendWith(MockitoExtension.class)` and mock `LlmClient`.

### Tests required

- `defineStack_shouldReturnParsedArchitectureDoc`
  Mock `llmClient.chat()` to return valid JSON with all 5 fields populated.
  Verify all ArchitectureDoc fields match.

- `defineStack_shouldHandleMarkdownFencedResponse`
  Mock response with ` ```json ` fences around valid JSON.
  Verify ArchitectureDoc is parsed correctly.

- `defineStack_shouldUseDefaultsForMissingFields`
  Mock response: `{"stack": "Java 21"}` (no services, constraints, deploymentModel, rationale)
  Verify services and constraints are empty lists, deploymentModel and rationale are empty strings.

- `defineStack_shouldThrowPipelineException_onNullContext`
  Pass null context.
  Verify PipelineException with phase "Phase3.5"

- `defineStack_shouldThrowPipelineException_onEmptyEnrichedSummary`
  Pass IdeaContext with blank enrichedSummary.
  Verify PipelineException with phase "Phase3.5"

- `defineStack_shouldThrowPipelineException_onNullDocuments`
  Pass null documents.
  Verify PipelineException with phase "Phase3.5"

- `defineStack_shouldThrowPipelineException_onEmptyDocument`
  Pass PipelineOutput with blank contextDocument.
  Verify PipelineException with phase "Phase3.5"

- `defineStack_shouldThrowPipelineException_onEmptyLlmResponse`
  Mock `llmClient.chat()` to return `""`
  Verify PipelineException with phase "Phase3.5"

- `defineStack_shouldThrowPipelineException_onInvalidJson`
  Mock `llmClient.chat()` to return `"not json"`
  Verify PipelineException with phase "Phase3.5"

- `defineStack_shouldIncludeAllSectionsInUserMessage`
  Use ArgumentCaptor to capture userMessage.
  Verify it contains `"=== IDEA CONTEXT ==="`, `"=== CONTEXT DOCUMENT ==="`, `"=== FLOW DOCUMENT ==="`, `"=== TASK DOCUMENT ==="`.

- `defineStack_shouldIncludeDebateHistoryInUserMessage`
  Pass a non-empty `debateHistory` (e.g. 2 messages with known agentName + content).
  Use ArgumentCaptor to capture userMessage.
  Verify it contains `"=== DEBATE HISTORY ==="` and each message formatted as `"[agentName]: content"`.

- `defineStack_shouldOmitDebateHistorySectionWhenHistoryIsEmpty`
  Pass an empty `debateHistory` list.
  Use ArgumentCaptor to capture userMessage.
  Verify userMessage does NOT contain `"=== DEBATE HISTORY ==="`.

- `defineStack_shouldOmitDebateHistorySectionWhenHistoryIsNull`
  Pass `null` as `debateHistory`.
  Verify no exception is thrown and userMessage does NOT contain `"=== DEBATE HISTORY ==="`.

---

## Acceptance criteria
1. `mvn clean compile` → SUCCESS
2. `mvn test` → 13 new tests pass (StackArchitectOrchestratorTest)
3. `mvn test` → 64 total tests passing
4. `defineStack()` has no hardcoded placeholder values
5. Missing JSON fields use safe defaults (not null, not exception)
6. Missing JSON arrays default to empty lists
7. All JSON parsing via `JsonParser.parse()`
8. System prompt defined as private static final constant
9. `debateHistory` is included in `userMessage` when non-empty, omitted when null or empty
10. Null `debateHistory` does not throw — treated as empty
11. Null or blank `enrichedSummary` throws `PipelineException`
12. Null or blank document fields throw `PipelineException`

---
