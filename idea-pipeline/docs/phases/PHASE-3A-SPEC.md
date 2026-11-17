# PHASE-3A-SPEC: Critique (Document Audit)

**Date:** 2026-05-24
**Version:** 1.0
**Status:** ✅ IMPLEMENTED

---

## 1. Objective

Audit the three synthesized documents (`context.md`, `flows.md`, `tasks.md`) to detect inconsistencies, gaps and topics that must go back to the debate. This phase is the first of the convergence loop (3a → 3b → possible loop to Phase 1).

---

## 2. Inputs

| Input | Type | Source | Description |
|-------|------|--------|-------------|
| `docs` | `PipelineOutput` | Phase 2 (Synthesis) | Three documents: `contextDocument`, `flowDocument`, `taskDocument` in Markdown format |
| `debateHistory` | `List<DebateMessage>` | Phase 1 (Debate) | Full history of the multi-agent debate |

---

## 3. Output

`CritiqueResult` — record with three lists:

```json
{
  "critiques": ["critique 1", "critique 2"],
  "gaps": ["gap 1"],
  "refinementNeeds": ["topic to re-debate 1"]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `critiques` | `List<String>` | Concrete problems found (contradictions, vagueness, unrealistic assumptions) |
| `gaps` | `List<String>` | What is completely missing (flows, roles, error handling, integrations) |
| `refinementNeeds` | `List<String>` | Topics that must go back to the debate (unresolved trade-offs, ambiguous scope) |

---

## 4. Behavior

### 4.1 Main flow

1. Validate that `docs` and `debateHistory` are not `null`
2. Build `userMessage` with the 3 document sections + formatted debate history
3. Call the LLM with `CRITIQUE_SYSTEM_PROMPT`
4. Parse the JSON response into `CritiqueResult`
5. Return the result

### 4.2 System Prompt

The LLM acts as a **product and architecture auditor**. It must be rigorous but constructive. Every finding must be specific and actionable. 3-7 items per array are expected.

### 4.3 Token Budget

If `debateHistory` exceeds 30 messages, it is truncated to:
- First 5 messages
- Last 10 messages
- Truncation marker: `... (N messages omitted) ...`

---

## 5. Error handling

| Condition | Exception | Phase | LoopCount |
|-----------|-----------|-------|-----------|
| `docs == null` | `PipelineException("docs is null", "Phase3a", 0)` | Phase3a | 0 |
| `debateHistory == null` | `PipelineException("debateHistory is null", "Phase3a", 0)` | Phase3a | 0 |
| Empty/null LLM response | `PipelineException("Empty response from LLM", "Phase3a", 0)` | Phase3a | 0 |
| Invalid JSON | `PipelineException("Critique failed", "Phase3a", 0, cause)` | Phase3a | 0 |
| LLM error | `PipelineException("Critique failed", "Phase3a", 0, cause)` | Phase3a | 0 |

### Re-throwing rule
- `PipelineException` is re-thrown as-is
- `RuntimeException` is wrapped in `PipelineException`

---

## 6. Acceptance criteria

- [x] `CriticOrchestrator` uses `LlmClient` injected via constructor — no hardcoded logic
- [x] System prompt defined as `private static final String`
- [x] All 3 documents appear in the LLM prompt
- [x] The debate history is formatted as `[agentName] (round N): [content]`
- [x] Token budget applied when `debateHistory.size() > 30`
- [x] Missing JSON fields (gaps, refinementNeeds) default to an empty list
- [x] Successful `mvn compile`
- [x] 10 unit tests pass (`CriticOrchestratorTest`)

---

## 7. Tests

See `CriticOrchestratorTest.java` (10 tests):

| # | Test | What it verifies |
|---|------|------------------|
| 1 | `critique_shouldReturnParsedCritiqueResult` | Correct parsing of complete JSON |
| 2 | `critique_shouldThrowPipelineException_onInvalidJson` | Invalid JSON → exception |
| 3 | `critique_shouldThrowPipelineException_onEmptyResponse` | Empty response → exception |
| 4 | `critique_shouldHandleMarkdownFencedResponse` | JSON inside ```json fences |
| 5 | `critique_shouldIncludeAllThreeDocumentsInPrompt` | All 3 docs in the userMessage |
| 6 | `critique_shouldFormatDebateHistoryInUserMessage` | Format `agentName (round N): content` |
| 7 | `critique_shouldHandleEmptyDebateHistory` | `List.of()` → "(No debate history available)" |
| 8 | `critique_shouldHandleMissingJsonFields` | Missing fields → empty lists |
| 9 | `critique_shouldThrowPipelineException_onLlmFailure` | RuntimeException → PipelineException |
| 10 | `critique_shouldTruncateDebateHistoryOver30Messages` | Truncation to 5+10 with notice |

---

## 8. Files involved

| File | Role |
|------|------|
| `CriticOrchestrator.java` | Implementation |
| `CriticOrchestratorTest.java` | 10 unit tests |
| `CritiqueResult.java` | Output record (unmodified) |
| `PipelineOutput.java` | Input record (unmodified) |

---

*End of spec.*
