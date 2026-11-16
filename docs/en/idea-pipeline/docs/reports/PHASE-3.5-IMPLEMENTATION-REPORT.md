# PHASE-3.5-IMPLEMENTATION-REPORT: Stack Architecture

**Date:** 2026-05-24
**Version:** 1.0

---

## Summary

Phase 3.5 implemented successfully. `StackArchitectOrchestrator` defines the complete technology stack (language, services, constraints, deployment, rationale) using an LLM acting as a senior architect, replacing the previous hardcoded placeholder.

---

## What was done

### Implementation

- **`StackArchitectOrchestrator.java`**: Fully rewritten
  - `LlmClient` injection via constructor
  - `ARCHITECT_SYSTEM_PROMPT` system prompt as a constant — senior architect with 15+ years
  - `defineStack(IdeaContext, PipelineOutput, List<DebateMessage>)` method with a real LLM call
  - Private `buildArchitectUserMessage()` with:
    - `=== IDEA CONTEXT ===` (enriched summary)
    - Three document sections (`context.md`, `flows.md`, `tasks.md`)
    - `--- DEBATE INSIGHTS ---` with formatted history
    - Token budget: truncation to 5 + 10 when > 30 messages
  - `extractStringList()` helper for tolerant parsing
  - Validation of required fields: `stack` not blank, `services` not empty
  - Missing `constraints` → `List.of()` (tolerant)

### Tests

- **`StackArchitectOrchestratorTest.java`**: 11 unit tests
  - Correct parsing of the 5 fields (stack, services, constraints, deploymentModel, rationale)
  - Invalid JSON, empty response, LLM error → `PipelineException`
  - Empty stack → `PipelineException("Empty stack")`
  - Empty services → `PipelineException("Empty services")`
  - Verification of content in userMessage (enrichedSummary + 3 docs + debate)
  - Debate history correctly formatted
  - Markdown fences stripping
  - Missing constraints → empty list
  - Empty debate history → "(No debate history available)"

---

## Decisions taken

| Decision | Reason |
|----------|-------|
| 5 fields in `ArchitectureDoc` | Full coverage: stack, services, constraints, deployment, rationale |
| `constraints` is optional (default `[]`) | Not all projects have explicit non-functional constraints |
| `stack` and `services` are required | Without stack or services, the architecture is not actionable |
| System prompt asks for 3-8 services/constraints | Avoids trivial answers; forces thinking about topology |
| Prompt includes enrichedSummary + docs + debate | The architect needs full context to decide |

---

## Test results

```
StackArchitectOrchestratorTest: 11/11 ✅
Total suite: 62/62 ✅ (no regressions)
```

---

## Known issues

None.

---

## Follow-ups

- [x] Phase 4 (Planning Poker) — consumes the `ArchitectureDoc` generated here
- [x] The `ArchitectureDoc` is passed to `PlanningPokerOrchestrator.runPlanningPoker()`

---

*End of report.*
