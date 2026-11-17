# PHASE-3B-IMPLEMENTATION-REPORT: Validation

**Date:** 2026-05-24
**Version:** 1.0

---

## Summary

Phase 3b implemented successfully. `ValidationOrchestrator` evaluates document convergence using a real LLM with a 0-100 rubric, replacing the dummy formula `score = max(10, 100 - loopCount * 5)`.

---

## What was done

### Implementation

- **`ValidationOrchestrator.java`**: Fully rewritten
  - `LlmClient` injection via constructor
  - `VALIDATION_SYSTEM_PROMPT` system prompt as a constant — 0-100 rubric with 4 levels
  - `validate(PipelineOutput, CritiqueResult, int loopCount)` method with a real LLM call
  - Private `buildValidationUserMessage()` with:
    - Highlighted `=== LOOP COUNT: N ===`
    - Three document sections
    - Audit results (critiques, gaps, refinementNeeds)
    - `(none)` for empty lists
  - Post-parse range validation: score must be 0-100
  - **No dummy formula** — the LLM is verified with `verify(llmClient, times(1)).chat(...)`

### Tests

- **`ValidationOrchestratorTest.java`**: 12 unit tests
  - Correct parsing of score, shouldLoop, reasoning
  - Low score (35) → shouldLoop=true
  - Out-of-range score (150) → `PipelineException`
  - Verification of loopCount in userMessage
  - Verification of documents + critique results in userMessage
  - Markdown fences stripping
  - Empty critique lists → "(none)"
  - **`validate_shouldNotUseDummyFormula`**: verifies that `llmClient.chat()` was called

---

## Decisions taken

| Decision | Reason |
|----------|-------|
| 0-100 rubric with 4 levels | Clear for the LLM; thresholds documented in the system prompt |
| `loopCount` influences tolerance | Loop 0 = rigorous, Loop 2+ = permissive; the LLM receives it explicitly |
| Score range validation post-parse | Defense against LLM hallucinations (scores > 100 or negative) |
| `maxLoops` is NOT enforced here | `IdeaPipeline` keeps that policy; separation of concerns |
| No dummy formula | Verified with `verify(llmClient).chat()` — if it fails, the dummy is still active |

---

## Test results

```
ValidationOrchestratorTest: 12/12 ✅
Total suite: 51/51 ✅ (no regressions)
```

---

## Known issues

None.

---

## Follow-ups

- [x] Phase 3.5 (Stack Architecture) — runs when `shouldLoop == false`
- [x] `IdeaPipeline` consumes `ValidationResult.shouldLoop()` to decide whether to iterate

---

*End of report.*
