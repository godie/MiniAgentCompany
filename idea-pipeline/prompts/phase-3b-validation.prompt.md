# phase-3b-validation.prompt.md v1.1

## Role
You are a Senior Java Developer implementing Phase 3b of the idea-pipeline system.
Phase 3b receives the 3 documents + the critique from Phase 3a and decides
whether the pipeline has converged or needs another loop.

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
- `PipelineOutput.java`, `CritiqueResult.java`, `ValidationResult.java` — records, do not modify:
  - `CritiqueResult(List<String> critiques, List<String> gaps, List<String> refinementNeeds)`
  - `ValidationResult(int convergenceScore, boolean shouldLoop, String reasoning)`
- `PipelineConfig.java` — has PipelineProperties with convergenceThreshold and maxLoops
- `PipelineProperties.java` — inject to read convergenceThreshold
- `PipelineException.java` — constructors:
  - `PipelineException(String message, String phase, int loopCount)`
  - `PipelineException(String message, String phase, int loopCount, Throwable cause)`
- `ValidationOrchestrator.java` — YOUR TARGET FILE.
  Inject `LlmClient` and `PipelineProperties` via constructor.

## Project status
- mvn clean compile → SUCCESS
- mvn test → 40/40 passing

---

## Your task

Implement `ValidationOrchestrator.java` — one method: `validate()`

---

## System prompt — define as private static final String constant

```java
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
```

---

## Method: validate()

**Signature:**
```java
public ValidationResult validate(
    PipelineOutput docs,
    CritiqueResult critique,
    int loopCount
)
```

**Purpose:** Send the 3 documents + critique to the LLM validator and parse the JSON response. The `shouldLoop` field in the response is advisory — `IdeaPipeline` enforces the maxLoops limit, NOT this method.

**Exact userMessage format:**

Each list from `CritiqueResult` is serialized line by line, one item per line, with prefix `- `. If a list is null or empty, write `- (none)` instead.

```
=== DOCUMENTS ===
--- CONTEXT ---
{docs.contextDocument()}
--- FLOWS ---
{docs.flowDocument()}
--- TASKS ---
{docs.taskDocument()}
=== CRITIQUE (iteration {loopCount}) ===
Critiques:
- critique item 1
- critique item 2

Gaps:
- gap item 1

Refinement needs:
- refinement item 1
- refinement item 2
```

**Concrete example** — given `loopCount = 2` and a `CritiqueResult` with:
- `critiques = ["Missing error handling", "Inconsistent naming"]`
- `gaps = ["No deployment section"]`
- `refinementNeeds = []`

The critique section must render as:
```
=== CRITIQUE (iteration 2) ===
Critiques:
- Missing error handling
- Inconsistent naming

Gaps:
- No deployment section

Refinement needs:
- (none)
```

**Implementation steps:**

1. Validate inputs:
   - If `docs` is null: throw `PipelineException("docs is null", "Phase3b", 0)`
   - If `critique` is null: throw `PipelineException("critique is null", "Phase3b", loopCount)`
   - If `loopCount < 0`: throw `PipelineException("loopCount must be >= 0", "Phase3b", loopCount)`

2. Build a private helper `formatList(List<String> items)` that returns a String:
   - If `items` is null or empty: return `"- (none)"`
   - Otherwise: join each item as `"- " + item`, one per line (`\n`)

3. Build `userMessage` using the exact format above, calling `formatList()` for each of the three critique lists

4. Call `llmClient.chat(VALIDATION_SYSTEM_PROMPT, userMessage)`

5. If response is null or blank:
   throw `PipelineException("Empty response from validation LLM", "Phase3b", loopCount)`

6. Parse with `JsonParser.parse(response)` — wrap RuntimeException:
   throw `PipelineException("Failed to parse validation JSON", "Phase3b", loopCount, e)`

7. Extract from JsonNode:
   - `convergenceScore` → int (default `0` if missing)
   - `shouldLoop` → boolean (default `true` if missing)
   - `reasoning` → String (default `""` if missing)

8. Return `new ValidationResult(convergenceScore, shouldLoop, reasoning)`

**Logging:**
- `log.info()` on entry: loopCount + size of each critique list
- `log.info()` after parsing: convergenceScore and shouldLoop
- `log.warn()` if `convergenceScore < convergenceThreshold`
- `log.error()` on any caught exception before rethrowing

---

## Coding standards
- Constructor injection only
- @Service + @Slf4j
- No @Autowired on fields
- All JSON parsing via `JsonParser.parse()`
- System prompt as private static final String constant
- Missing JSON fields use safe defaults (never null, never exception)
- `formatList()` is a private helper method — not exposed publicly
- This method does NOT enforce maxLoops — that is IdeaPipeline's responsibility

---

## Testing requirements (TDD)

Test file: `src/test/java/com/ideapipeline/orchestrator/ValidationOrchestratorTest.java`

### Tests required

- `validate_shouldReturnParsedValidationResult`
  Mock response: `{"convergenceScore": 85, "shouldLoop": false, "reasoning": "good"}`
  Verify convergenceScore == 85, shouldLoop == false, reasoning == "good"

- `validate_shouldHandleMarkdownFencedResponse`
  Mock response with ` ```json ` fences.
  Verify ValidationResult is parsed correctly.

- `validate_shouldUseDefaultsForMissingFields`
  Mock response: `{"convergenceScore": 50}` (no shouldLoop, no reasoning)
  Verify shouldLoop == true, reasoning == ""

- `validate_shouldThrowPipelineException_onNullDocs`
  Verify PipelineException with phase "Phase3b"

- `validate_shouldThrowPipelineException_onNullCritique`
  Verify PipelineException with phase "Phase3b"

- `validate_shouldThrowPipelineException_onNegativeLoopCount`
  Pass loopCount = -1.
  Verify PipelineException with phase "Phase3b"

- `validate_shouldThrowPipelineException_onEmptyLlmResponse`
  Mock `llmClient.chat()` to return `""`
  Verify PipelineException with phase "Phase3b"

- `validate_shouldThrowPipelineException_onInvalidJson`
  Mock `llmClient.chat()` to return `"not json"`
  Verify PipelineException with phase "Phase3b"

- `validate_shouldIncludeLoopCountInUserMessage`
  Use ArgumentCaptor to capture userMessage.
  Verify it contains `"iteration 2"` when loopCount == 2.

- `validate_shouldFormatCritiqueItemsAsBulletLines`
  Pass CritiqueResult with known critiques `["C1", "C2"]`, gaps `["G1"]`, refinementNeeds `[]`.
  Use ArgumentCaptor to capture userMessage.
  Verify userMessage contains:
  - `"- C1\n- C2"` under `Critiques:`
  - `"- G1"` under `Gaps:`
  - `"- (none)"` under `Refinement needs:`

- `validate_shouldRenderNoneForNullOrEmptyLists`
  Pass CritiqueResult with all three lists empty.
  Use ArgumentCaptor.
  Verify userMessage contains `"- (none)"` exactly 3 times.

---

## Acceptance criteria
1. `mvn clean compile` → SUCCESS
2. `mvn test` → 11 new tests pass (ValidationOrchestratorTest)
3. `mvn test` → 51 total tests passing
4. `validate()` has no `UnsupportedOperationException`
5. Missing JSON fields use safe defaults (not null, not exception)
6. This method does NOT enforce maxLoops
7. `convergenceScore < threshold` triggers `log.warn()`
8. Each critique list item appears as `"- item"` in the userMessage
9. Null or empty list renders as `"- (none)"`
10. `loopCount < 0` throws PipelineException
11. System prompt defined as private static final constant

---
