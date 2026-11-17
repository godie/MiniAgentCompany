# PHASE-4-IMPLEMENTATION-REPORT: Planning Poker

**Date:** 2026-05-25
**Version:** 1.0

---

## Summary

Phase 4 implemented successfully. `PlanningPokerOrchestrator` transforms `tasks.md` + `ArchitectureDoc` + `Team` into a fully estimated `TaskGraph` using two consecutive LLM calls: a Markdown parser (Phase 4A) and a simulated Planning Poker session (Phase 4B). It replaces the previous hardcoded placeholder.

---

## What was done

### Implementation

- **`PlanningPokerOrchestrator.java`**: Fully rewritten
  - `LlmClient` injection via constructor
  - **Two system prompts** as constants:
    - `TASK_PARSE_SYSTEM_PROMPT` — precise Markdown-to-JSON parser
    - `PLANNING_POKER_SYSTEM_PROMPT` — Scrum Master facilitating Planning Poker with Fibonacci
  - Public method `runPlanningPoker(String tasksMarkdown, Team team, ArchitectureDoc architecture)`
  - Private method `parseTasksFromMarkdown(String)` — Phase 4A
  - Private method `buildPlanningPokerUserMessage(...)` — Phase 4B
  - **Cross-validation** of `totalPoints`: compares the value declared by the LLM vs the computed sum of `storyPoints` (warning on mismatch)
  - ID validation: each `taskId` in the LLM response must exist in the parsed tasks
  - Empty Markdown → empty `TaskGraph` (no exception)
  - Handling of `votes`, `dependencies`, `adjacency`, `pointsByRole` from the LLM JSON

### Prerequisite: ADR-002

Before implementing Phase 4, the `tasks.md` format was hardened:
- Sequential IDs: `EPIC-1`, `US-1`, `TASK-1`
- `<!-- META: {"id":"...","dependencies":[...]} -->` blocks
- Explicit `Dependencies` field
- See `docs/decisions/ADR-002-tasks-markdown-format.md`

### Tests

- **`PlanningPokerOrchestratorTest.java`**: 14 unit tests
  - **4 parser tests**: task extraction, ID preservation, empty markdown, invalid JSON
  - **10 orchestrator tests**: complete TaskGraph, invalid JSON, empty response, team members in prompt, architecture in prompt, empty team, null markdown, unknown taskId, markdown fences, totalPoints mismatch

---

## Decisions taken

| Decision | Reason |
|----------|-------|
| Two LLM phases (parse + estimate) | Separation of concerns; the parser must not estimate, the estimator must not parse |
| Empty Markdown → empty `TaskGraph` | It is not an error; it can happen if Synthesis generated no tasks |
| `totalPoints` cross-validation is non-blocking | Warning only; the LLM may have a summation error but the individual estimates are correct |
| Fibonacci (1,2,3,5,8,13,21) | Industry standard for Planning Poker |
| `pointsByRole` uses `BaseRole` enum values | Consistent with the project's data model |
| `adjacency` as a DAG | Represents dependencies between tasks; the LLM must not produce cycles |

---

## Test results

```
PlanningPokerOrchestratorTest: 14/14 ✅
Total suite: 76/76 ✅ (no regressions)
```

Later, with controller + integration tests: **87/87 ✅**

---

## Known issues

| Issue | Severity | Status |
|-------|-----------|--------|
| `totalPoints` may not match the sum of storyPoints | Low | Warning only; does not block the pipeline |
| The LLM parser may fail with very non-standard Markdown | Medium | Mitigated by ADR-002 (hardened format) |

---

## Follow-ups

- [x] Controller tests (`PipelineControllerTest`) — 8 tests
- [x] Integration tests (`IdeaPipelineIntegrationTest`) — 3 tests
- [x] API gap closure (`RunRequest.answers`)
- [x] Final post-MVP documentation

---

*End of report.*
