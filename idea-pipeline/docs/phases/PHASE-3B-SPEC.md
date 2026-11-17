# PHASE-3B-SPEC: Validation (Convergence Evaluation)

**Date:** 2026-05-24
**Version:** 1.0
**Status:** ✅ IMPLEMENTED

---

## 1. Objective

Evaluate whether the three synthesized documents have converged sufficiently to move on to architecture definition (Phase 3.5), or whether another debate→synthesis cycle is needed. This phase replaces a dummy formula (`score = max(10, 100 - loopCount * 5)`) with real LLM evaluation.

---

## 2. Inputs

| Input | Type | Source | Description |
|-------|------|--------|-------------|
| `docs` | `PipelineOutput` | Phase 2 (Synthesis) | Three converged documents |
| `critique` | `CritiqueResult` | Phase 3a (Critic) | Result of the document audit |
| `loopCount` | `int` | `IdeaPipeline` | Current iteration number of the convergence loop |

---

## 3. Output

`ValidationResult` — record:

```json
{
  "convergenceScore": 82,
  "shouldLoop": false,
  "reasoning": "The documents are coherent with each other. There are no blocking gaps."
}
```

| Field | Type | Description |
|-------|------|-------------|
| `convergenceScore` | `int` | Document convergence score 0-100 |
| `shouldLoop` | `boolean` | `true` = go back to debate+synthesis, `false` = move on to architecture |
| `reasoning` | `String` | Justification of the decision (max 150 words) |

---

## 4. Convergence rubric

| Range | Meaning | Action |
|-------|---------|--------|
| 0–40 | CRITICAL — Major inconsistencies, missing sections | Loop mandatory |
| 41–74 | NEEDS WORK — Significant gaps but correct direction | Loop recommended |
| 75–89 | GOOD — Coherent documents, minor acceptable gaps | Proceed |
| 90–100 | EXCELLENT — Complete and consistent documents | Proceed |

### Influence of loopCount
- `loopCount = 0` (first pass): rigorous evaluation
- `loopCount >= 2`: more permissive evaluation (the system is converging)

---

## 5. Behavior

### 5.1 Main flow

1. Validate that `docs` and `critique` are not `null`
2. Build `userMessage` with:
   - Loop count highlighted
   - The 3 documents
   - Audit results (critiques, gaps, refinementNeeds)
3. Call the LLM with `VALIDATION_SYSTEM_PROMPT`
4. Parse the JSON response into `ValidationResult`
5. Validate that `convergenceScore` is in the 0-100 range
6. Return the result

### 5.2 Loop responsibility

`ValidationOrchestrator` does **NOT** enforce `maxLoops` — that is the responsibility of `IdeaPipeline`. The orchestrator only computes the score and recommendation.

---

## 6. Error handling

| Condition | Exception | Phase | LoopCount |
|-----------|-----------|-------|-----------|
| `docs == null` | `PipelineException("docs is null", "Phase3b", loopCount)` | Phase3b | dynamic |
| `critique == null` | `PipelineException("critique is null", "Phase3b", loopCount)` | Phase3b | dynamic |
| Empty/null LLM response | `PipelineException("Empty response from LLM", "Phase3b", loopCount)` | Phase3b | dynamic |
| Score out of range (0-100) | `PipelineException("Invalid convergenceScore: X (must be 0-100)", "Phase3b", loopCount)` | Phase3b | dynamic |
| Invalid JSON | `PipelineException("Validation failed", "Phase3b", loopCount, cause)` | Phase3b | dynamic |
| LLM error | `PipelineException("Validation failed", "Phase3b", loopCount, cause)` | Phase3b | dynamic |

---

## 7. Acceptance criteria

- [x] `ValidationOrchestrator` uses `LlmClient` injected via constructor
- [x] **No dummy formula** — the score comes 100% from the LLM
- [x] System prompt defined as `private static final String` with a 0-100 rubric
- [x] `loopCount` included prominently in the prompt
- [x] Score validated in the 0-100 range post-parsing
- [x] The 3 documents + critique results appear in the prompt
- [x] Empty critique lists display "(none)"
- [x] Successful `mvn compile`
- [x] 12 unit tests pass (`ValidationOrchestratorTest`)

---

## 8. Tests

See `ValidationOrchestratorTest.java` (12 tests):

| # | Test | What it verifies |
|---|------|------------------|
| 1 | `validate_shouldReturnParsedValidationResult` | Correct parsing: score, shouldLoop, reasoning |
| 2 | `validate_shouldReturnShouldLoopTrue_whenScoreIsLow` | Score 35 → shouldLoop=true |
| 3 | `validate_shouldThrowPipelineException_onInvalidJson` | Invalid JSON → exception |
| 4 | `validate_shouldThrowPipelineException_onEmptyResponse` | Empty response → exception |
| 5 | `validate_shouldThrowPipelineException_onScoreOutOfRange` | Score 150 → exception "must be 0-100" |
| 6 | `validate_shouldIncludeLoopCountInPrompt` | `=== LOOP COUNT: N ===` in userMessage |
| 7 | `validate_shouldIncludeAllDocumentsInPrompt` | All 3 docs in userMessage |
| 8 | `validate_shouldIncludeCritiqueResultsInPrompt` | Critiques and gaps in userMessage |
| 9 | `validate_shouldHandleMarkdownFencedResponse` | JSON inside ```json fences |
| 10 | `validate_shouldHandleEmptyCritiqueLists` | Empty lists → "(none)" |
| 11 | `validate_shouldThrowPipelineException_onLlmFailure` | RuntimeException → PipelineException |
| 12 | `validate_shouldNotUseDummyFormula` | `verify(llmClient, times(1)).chat(...)` — confirms the LLM was called |

---

## 9. Files involved

| File | Role |
|------|------|
| `ValidationOrchestrator.java` | Implementation |
| `ValidationOrchestratorTest.java` | 12 unit tests |
| `ValidationResult.java` | Output record (unmodified) |
| `PipelineOutput.java` | Input record (unmodified) |
| `CritiqueResult.java` | Input record (unmodified) |

---

*End of spec.*
