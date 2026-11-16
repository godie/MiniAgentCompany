# phase-3b-validation.prompt.md v1.0

## Role
You are a Senior Java Developer implementing Phase 3b of the idea-pipeline system.
Phase 3b evaluates whether the three synthesized documents have converged
sufficiently to advance to architecture definition, or whether another
debate→synthesis loop is needed.

## Context
Read these files before writing any code:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

---

## What already exists — do not modify

These files are FULLY IMPLEMENTED and tested:

- `LlmClient.java` — interface:
  - `chat(String systemPrompt, String userMessage)`
  - `chatWithHistory(String systemPrompt, List<DebateMessage> history, String userMessage)`

- `LlmClientImpl.java` — @Component, fully implemented. 6 tests passing.

- `JsonParser.java` — package `com.ideapipeline.client`
  `public static JsonNode parse(String response)`

- `GatekeeperOrchestrator.java` — fully implemented. 8 tests passing.
- `DebateOrchestrator.java` — fully implemented. 9 tests passing.
- `SynthesisOrchestrator.java` — fully implemented. 8 tests passing.
- `CriticOrchestrator.java` — fully implemented. 9 tests passing.

- `PipelineOutput.java` — record, do not modify.
- `CritiqueResult.java` — record, do not modify.
- `ValidationResult.java` — record, do not modify:
  `ValidationResult(int convergenceScore, boolean shouldLoop, String reasoning)`

- `PipelineException.java` — constructors available.

- `IdeaPipeline.java` — handles `maxLoops` policy externally.
  Your orchestrator does NOT enforce maxLoops; it only computes the score
  and `shouldLoop` recommendation.

- `ValidationOrchestrator.java` — YOUR TARGET FILE
  Currently uses dummy formula: `score = max(10, 100 - loopCount * 5)`.
  Inject `LlmClient` via constructor:
  `public ValidationOrchestrator(LlmClient llmClient)`

---

## Project status
- `mvn clean compile` → SUCCESS
- `mvn test` → 38/38 passing

---

## Your task

Implement `ValidationOrchestrator.java` — one method: `validate()`

---

### System prompt constant

```java
private static final String VALIDATION_SYSTEM_PROMPT = """
You are a convergence evaluator for a software design pipeline.
You receive three documents (context, flows, tasks) and the results
of a prior audit (critiques, gaps, refinement needs).

Your job is to decide whether the documents are sufficiently coherent
and complete to advance to architecture definition.

Score rubric (0-100):

0-40 — CRITICAL: Major inconsistencies, contradictions, or missing sections.
        Must loop. Documents are not usable.

41-74 — NEEDS WORK: Significant gaps or vague sections exist, but the core
         is directionally correct. Looping is recommended but not forced.

75-89 — GOOD: Documents are coherent and mostly complete. Minor gaps
         are acceptable. Should advance.

90-100 — EXCELLENT: Documents are thorough, consistent, and ready for
          architecture definition. Definitely advance.

Your shouldLoop recommendation:
- true  → send back to debate+synthesis
- false → advance to architecture

IMPORTANT: Consider the loopCount in your reasoning.
- If loopCount >= 2, be more permissive (the system is converging slowly).
- If this is the first pass (loopCount = 0), be rigorous.

Respond ONLY in JSON with this exact format, no preamble, no markdown:
{
  "convergenceScore": 82,
  "shouldLoop": false,
  "reasoning": "Brief explanation of the score and decision (max 150 words)"
}
""";
```

---

## Implementation: `validate()`

**Signature:**
```java
public ValidationResult validate(PipelineOutput docs, CritiqueResult critique, int loopCount)
```

**Purpose:** Evaluate document convergence using an LLM to assign a score
and decide whether another debate→synthesis loop is needed.

---

### Implementation steps

1. **Validate inputs**
   - If `docs == null`: throw `PipelineException("docs is null", "Phase3b", loopCount)`
   - If `critique == null`: throw `PipelineException("critique is null", "Phase3b", loopCount)`

2. **Build user message**

   ```
   === LOOP COUNT: [loopCount] ===

   --- CONTEXT DOCUMENT ---
   [docs.contextDocument()]

   --- FLOW DOCUMENT ---
   [docs.flowDocument()]

   --- TASK DOCUMENT ---
   [docs.taskDocument()]

   --- AUDIT RESULTS ---
   Critiques found:
   - [critique.critiques() joined by "\n- "]

   Gaps identified:
   - [critique.gaps() joined by "\n- "]

   Refinement needs:
   - [critique.refinementNeeds() joined by "\n- "]
   ```

   - If any critique list is empty, write: `(none)`
   - Include the loopCount prominently so the LLM can factor it into
     convergence tolerance

3. **Call LLM**
   ```java
   String response = llmClient.chat(VALIDATION_SYSTEM_PROMPT, userMessage);
   ```

4. **Validate response**
   - If `response == null || response.isBlank()`:
     throw `PipelineException("Empty response from LLM", "Phase3b", loopCount)`

5. **Parse JSON**
   ```java
   try {
       JsonNode node = JsonParser.parse(response);
       
       int convergenceScore = node.get("convergenceScore").asInt();
       boolean shouldLoop = node.get("shouldLoop").asBoolean();
       String reasoning = node.get("reasoning").asText();
       
       // Validate score range
       if (convergenceScore < 0 || convergenceScore > 100) {
           throw new PipelineException(
               "Invalid convergenceScore: " + convergenceScore + " (must be 0-100)",
               "Phase3b", loopCount);
       }
       
       return new ValidationResult(convergenceScore, shouldLoop, reasoning);
   } catch (PipelineException e) {
       throw e;
   } catch (RuntimeException e) {
       throw new PipelineException("Failed to parse validation JSON", "Phase3b", loopCount, e);
   }
   ```

---

## Logging

- `log.info()` on method entry: loopCount and document lengths
- `log.info()` after success: convergenceScore, shouldLoop, reasoning summary
- `log.warn()` if convergenceScore < 75 and shouldLoop == true (about to loop)
- `log.info()` if convergenceScore >= 75 and shouldLoop == false (advancing)
- `log.error()` on any caught exception before rethrowing

---

## Exception handling

```java
// Pattern:
try {
    // ... LLM call + JSON parsing ...
} catch (PipelineException e) {
    throw e;
} catch (RuntimeException e) {
    throw new PipelineException("Validation failed", "Phase3b", loopCount, e);
}
```

---

## Coding standards

- `@Service` + `@Slf4j` on class
- Constructor injection only — `public ValidationOrchestrator(LlmClient llmClient)`
- No `@Autowired` on fields
- System prompt as `private static final String` constant
- All JSON parsing via `JsonParser.parse()`
- All LLM exceptions wrapped in `PipelineException`
- No dummy formula — score MUST come from LLM evaluation
- Remove the placeholder `Math.max(10, 100 - loopCount * 5)` logic entirely
- Score range validation (0-100) after parsing

---

## Testing requirements (TDD)

Test file: `src/test/java/com/ideapipeline/orchestrator/ValidationOrchestratorTest.java`
Use `@ExtendWith(MockitoExtension.class)` and mock `LlmClient`.

### Required tests

1. **`validate_shouldReturnParsedValidationResult`**
   - Mock `llmClient.chat()` to return:
     ```json
     {"convergenceScore":82,"shouldLoop":false,"reasoning":"Documents are coherent"}
     ```
   - Verify `result.convergenceScore() == 82`
   - Verify `result.shouldLoop() == false`
   - Verify `result.reasoning()` equals `"Documents are coherent"`

2. **`validate_shouldReturnShouldLoopTrue_whenScoreIsLow`**
   - Mock response with `convergenceScore: 35, shouldLoop: true`
   - Verify `result.shouldLoop() == true`

3. **`validate_shouldThrowPipelineException_onInvalidJson`**
   - Mock `llmClient.chat()` to return `"not json"`
   - Verify `PipelineException` is thrown with phase `"Phase3b"`

4. **`validate_shouldThrowPipelineException_onEmptyResponse`**
   - Mock `llmClient.chat()` to return `""`
   - Verify `PipelineException` is thrown with phase `"Phase3b"`

5. **`validate_shouldThrowPipelineException_onScoreOutOfRange`**
   - Mock response with `convergenceScore: 150`
   - Verify `PipelineException` is thrown with message containing "must be 0-100"

6. **`validate_shouldIncludeLoopCountInPrompt`**
   - Use `ArgumentCaptor<String>` to capture `userMessage`
   - Verify it contains `"=== LOOP COUNT: 2 ==="` (or whatever loopCount is)

7. **`validate_shouldIncludeAllDocumentsInPrompt`**
   - Verify `userMessage` contains contextDocument, flowDocument, and taskDocument

8. **`validate_shouldIncludeCritiqueResultsInPrompt`**
   - Verify `userMessage` contains critique text and gap text

9. **`validate_shouldHandleMarkdownFencedResponse`**
   - Mock `llmClient.chat()` to return JSON wrapped in ```json fences
   - Verify `JsonParser` strips fences and result is correctly parsed

10. **`validate_shouldHandleEmptyCritiqueLists`**
    - Pass `CritiqueResult` with empty lists
    - Verify userMessage contains `"(none)"` for empty sections

11. **`validate_shouldThrowPipelineException_onLlmFailure`**
    - Mock `llmClient.chat()` to throw `RuntimeException("LLM error")`
    - Verify `PipelineException` is thrown with phase `"Phase3b"`

12. **`validate_shouldNotUseDummyFormula`**
    - This test verifies the orchestrator actually calls `llmClient.chat()`
    - Use `verify(llmClient, times(1)).chat(anyString(), anyString())`
    - If the verify fails, the dummy formula is still in use

---

## Acceptance criteria

1. `mvn clean compile` → SUCCESS
2. `mvn test` → 12 new tests pass (ValidationOrchestratorTest)
3. Total tests ≥ 50 passing
4. `validate()` has NO dummy formula — score from LLM only
5. Score range (0-100) is validated after parsing
6. `shouldLoop` decision comes from LLM, not hardcoded logic
7. `loopCount` is included in the LLM prompt
8. Null/empty LLM responses throw `PipelineException`
9. `JsonParser` RuntimeException is wrapped in `PipelineException`
10. All three documents and critique results appear in the prompt
11. System prompt defined as `private static final String` constant
12. The orchestrator does NOT enforce `maxLoops` (that's `IdeaPipeline`'s job)

---

## Files to modify

1. **`src/main/java/com/ideapipeline/orchestrator/ValidationOrchestrator.java`**
   - Inject `LlmClient` via constructor
   - Replace dummy formula with LLM-based evaluation
   - Add score range validation

2. **`src/test/java/com/ideapipeline/orchestrator/ValidationOrchestratorTest.java`**
   - Create new test file (or replace existing if one exists)
   - 12 test methods as specified above

---

## Success criteria

After implementation:
- ✅ Code compiles without warnings
- ✅ All 12 tests pass
- ✅ No dummy formula remains
- ✅ LLM is actually called (verified by mock verification)
- ✅ Proper exception handling throughout
- ✅ Logging at appropriate levels

---

End of phase-3b-validation.prompt.md v1.0
