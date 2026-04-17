# phase-2-synthesis.prompt.md v1.0

Implement SynthesisOrchestrator.synthesize() — Phase 2 (Java)

Role
You are a Senior Java Developer implementing Phase 2 of the idea-pipeline system.
Phase 2 generates 3 documents in PARALLEL using Java 21 virtual threads and StructuredTaskScope, based on the debate history and idea context.

Context
Read before coding: META_PIPELINE.md, CONTEXT.md, AGENTS.md

Do not modify (already implemented and tested)
- `LlmClient.java` (interface): chat(String systemPrompt, String userMessage); chatWithHistory(...)
- `LlmClientImpl.java` — @Component, implemented
- `JsonParser.java` — com.ideapipeline.client; public static JsonNode parse(String response)
- `GatekeeperOrchestrator.java` — implemented
- `DebateOrchestrator.java` — implemented
- `IdeaContext.java`, `DebateMessage.java`, `PipelineOutput.java` — records (do not modify)
    - PipelineOutput(String contextDocument, String flowDocument, String taskDocument)
- `PipelineException.java` — constructors available
- Target file: `SynthesisOrchestrator.java`— currently throws UnsupportedOperationException
    - Inject LlmClient via constructor: public SynthesisOrchestrator(LlmClient llmClient)

Project status
- mvn clean compile → SUCCESS
- mvn test → 21/21 passing

Your task
Implement SynthesisOrchestrator.java — one method: synthesize()

System prompts — define as private static final String constants
```java
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
```
Method: synthesize()
Signature:
```java
public PipelineOutput synthesize(
IdeaContext context,
List<DebateMessage> debateHistory
) throws Exception{}
```
Purpose: Generate 3 documents in PARALLEL — context.md, flows.md, tasks.md — each by a separate LLM call running on a virtual thread. All 3 receive the same input: idea context + full debate history.

User message (shared for all 3 synthesizers)
Build the shared userMessage in this exact order:

Idea summary: [context.enrichedSummary()]
```
Debate history:
[agentName]: [content]
[agentName]: [content]
...
```
If debateHistory is empty, userMessage must be:

Idea summary: [context.enrichedSummary()]

No debate history available.

Implementation steps
1. Validate inputs:
    - If context == null: `throw PipelineException("context is null", "Phase2", 0)`
    - If debateHistory == null: `throw PipelineException("debateHistory is null", "Phase2", 0)`
    - If debateHistory is empty: do NOT throw; log.warn("No debate history — synthesizing from context only") and continue

2. Build shared user message:
    - Format each DebateMessage as "[agentName]: content"
    - Join with newline in the order provided
    - Prepend "Idea summary: [context.enrichedSummary()]\n\n" and "Debate history:" (or "No debate history available." if empty)

3. Launch 3 parallel tasks using StructuredTaskScope.ShutdownOnFailure:
    - Task 1: `llmClient.chat(CONTEXT_DOC_PROMPT, userMessage)` → contextDocument
    - Task 2: `llmClient.chat(FLOW_DOC_PROMPT, userMessage)` → flowDocument
    - Task 3: `llmClient.chat(TASK_DOC_PROMPT, userMessage)` → taskDocument

4. Call scope.join().throwIfFailed()

5. Validate each result — if null or blank:
   throw PipelineException("Empty document from synthesizer: [doc name]", "Phase2", 0)

6. Return new PipelineOutput(contextDocument, flowDocument, taskDocument)

Logging
- `log.info()` on method entry: debateHistory size (if debateHistory != null)
- `log.info()` after completion: confirm 3 documents generated
- `log.error()` on any caught exception before rethrowing

Exception handling
- `InterruptedException`: `Thread.currentThread().interrupt(); throw PipelineException("Synthesis interrupted", "Phase2", 0, e)`
- Any other exception: throw `PipelineException("Synthesis failed", "Phase2", 0, e)`

Coding standards
- @Service + @Slf4j on class
- Constructor injection only; no @Autowired on fields
- Use `StructuredTaskScope.ShutdownOnFailure` — not CompletableFuture
- All 3 system prompts as private static final String constants
- No hardcoded strings outside constants
- Null/blank document responses throw PipelineException

Testing requirements (TDD)
Test file: `src/test/java/com/ideapipeline/orchestrator/SynthesisOrchestratorTest.java`
Use @ExtendWith(MockitoExtension.class) and mock LlmClient.

Required tests
- `synthesize_shouldReturnPipelineOutputWithAllThreeDocuments`
  Mock llmClient.chat() to return "Context doc", "Flow doc", "Task doc" respectively.
  Verify PipelineOutput fields match.

- `synthesize_shouldCallLlmThreeTimes`
  Verify llmClient.chat() called exactly 3 times.

- `synthesize_shouldThrowPipelineException_onNullContext`
  Call synthesize(null, history) → expect PipelineException with phase "Phase2"

- `synthesize_shouldSucceedWithEmptyHistory`
  Call synthesize(context, List.of()) → expect success
  Verify userMessage contains "No debate history available"

- `synthesize_shouldThrowPipelineException_onEmptyDocumentResponse`
  Mock llmClient.chat() to return "" for all calls → expect PipelineException with phase "Phase2"

- `synthesize_shouldFormatDebateHistoryInUserMessage`
  Use ArgumentCaptor<String> to capture userMessage; verify it contains "[agentName]: content" for each DebateMessage

- `synthesize_shouldIncludeEnrichedSummaryInUserMessage`
  Verify userMessage contains context.enrichedSummary()

- `synthesize_shouldThrowPipelineException_onLlmFailure`
  Mock llmClient.chat() to throw RuntimeException("LLM error") → expect PipelineException with phase "Phase2"

Acceptance criteria
1. `mvn clean compile` → SUCCESS
2. `mvn test` → 8 new tests for SynthesisOrchestratorTest pass
3. `mvn test` → 29 total tests passing
4. `synthesize()` has no `UnsupportedOperationException`
5. Parallelism uses `StructuredTaskScope.ShutdownOnFailure`
6. All 3 LLM calls run in parallel — not sequential
7. `InterruptedException` re-interrupts the thread
8. Null/blank document responses throw `PipelineException`
9. All system prompts defined as private static final constants

Additional notes (comment in code)
// NOTE: if debateHistory exceeds 50 messages, consider summarizing
// before sending to LLM to avoid token limit issues.
// This is a known future improvement — not required for MVP.
