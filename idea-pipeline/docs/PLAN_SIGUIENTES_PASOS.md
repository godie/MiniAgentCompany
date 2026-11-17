# Plan for next steps

**Date:** 2026-05-25
**Version:** 1.1

## Plan objective
Complete the functional MVP of IdeaPipeline while keeping three principles:

- **Every phase with a clear contract.**
- **Every implementation guided by prompt + tests.**
- **All documentation persisted in `.md` or `.html` files.**

---

## Priority 0 — Base alignment ✅ COMPLETED (2026-05-24)

### Objective
Eliminate inconsistencies before expanding functionality.

### Deliverables
- ✅ Normalize `CONTEX.md` → `CONTEXT.md` — renamed.
- ✅ Confirm the MVP's official LLM provider: **Ollama (qwen3.5:4b)**.
- ✅ Review `application.yml`, `PipelineConfig`, prompts and docs — coherent.
- ✅ Document the decision in `docs/decisions/ADR-001-llm-provider.md`.
- ✅ Updated versions in `CONTEXT.md`, `META_PIPELINE.md`, `AGENTS.md`.

### Expected result
A single source of truth for:
- stack,
- LLM provider,
- key versions,
- names of the context files.

---

## Priority 1 — Implement Phase 3a (Critique) ✅ COMPLETED (2026-05-24)

### Objective
Replace the hardcoded critique with a real critique based on the LLM.

### Deliverables
- ✅ `prompts/phase-3a-critique.prompt.md`
- ✅ Real implementation of `CriticOrchestrator` — constructor injection of `LlmClient`, system prompt, token budget, `extractStringList()`
- ✅ Unit tests: `CriticOrchestratorTest` (10 tests)

### Result
Real `CritiqueResult` with: `critiques`, `gaps`, `refinementNeeds` from the LLM.

---

## Priority 2 — Implement Phase 3b (Validation) ✅ COMPLETED (2026-05-24)

### Objective
Compute `convergenceScore` and `shouldLoop` with a real LLM evaluation.

### Deliverables
- ✅ `prompts/phase-3b-validation.prompt.md`
- ✅ Real implementation of `ValidationOrchestrator` — constructor injection of `LlmClient`, system prompt with 0-100 rubric, range validation, no dummy formula
- ✅ Unit tests: `ValidationOrchestratorTest` (12 tests)

### Result
`ValidationResult` coherent with the generated documents and the prior critique. Dummy formula removed.

---

## Priority 3 — Implement Phase 3.5 (Stack Architecture) ✅ COMPLETED (2026-05-24)

### Objective
Generate a real `ArchitectureDoc` from the context and the converged docs.

### Deliverables
- ✅ `prompts/phase-3.5-stack-architecture.prompt.md`
- ✅ Real implementation of `StackArchitectOrchestrator` — constructor injection of `LlmClient`, senior architect system prompt, token budget, validation of required fields
- ✅ Unit tests: `StackArchitectOrchestratorTest` (11 tests)

### Result
Architecture consistent with the product type, constraints, scope and complexity. Without hardcoding.

---

## Priority 4 — Harden the `tasks.md` format ✅ COMPLETED (2026-05-24)

### Objective
Make `tasks.md` human-readable **and** robustly parseable.

### Deliverables
- ✅ `TASK_DOC_PROMPT` in `SynthesisOrchestrator.java` updated with sequential IDs, `<!-- META: {...} -->` blocks, dependency field and a complete example
- ✅ `docs/decisions/ADR-002-tasks-markdown-format.md` — canonical format documented with rules, examples and alternatives

### Format adopted
Markdown with metadata embedded in HTML comments:

```md
## Epic EPIC-1: Onboarding
<!-- META: {"id":"EPIC-1"} -->

### User Story US-1: Como usuario nuevo quiero registrarme
<!-- META: {"id":"US-1","epicId":"EPIC-1"} -->

#### Task TASK-1: Crear endpoint de registro
<!-- META: {"id":"TASK-1","epicId":"EPIC-1","userStory":"US-1","dependencies":[]} -->
- **Description:** Endpoint POST /api/auth/register
- **Acceptance criteria:** 
  - Debe validar email único
  - Debe hashear contraseña
- **Estimate:** M
- **Dependencies:** None
```

### Result
A safe base for implementing Phase 4 without fragile parsing. Stable IDs, explicit dependencies, dual readability (human + machine).

---

## Priority 5 — Implement Phase 4 (Planning Poker) ✅ COMPLETED (2026-05-25)

### Objective
Turn `tasks.md` + `ArchitectureDoc` + `Team` into a real `TaskGraph`.

### Deliverables
- ✅ `prompts/phase-4-planning-poker.prompt.md`
- ✅ `tasks.md` parser — private `parseTasksFromMarkdown()` with an LLM call (Phase 4A)
- ✅ Implementation of `PlanningPokerOrchestrator` — constructor injection of `LlmClient`, two system prompts, cross-validation of `totalPoints`
- ✅ Unit tests: `PlanningPokerOrchestratorTest` (14 tests: 4 parser + 10 orchestrator)
- ✅ Documentation of the expected `TaskGraph`

### Result
`TaskGraph` with: estimated tasks, explicit dependencies, total points, points by role, votes. Without hardcoding.

---

## Priority 6 — Close the API flow + end-to-end integration ✅ COMPLETED (2026-05-25)

### Objective
Ensure the system can run completely from the endpoints.

### Deliverables
- ✅ Extend `RunRequest` to include `List<RefinementQA>` (`answers` field).
- ✅ Fix `PipelineController` to use the real `team` (not dummy) and the real `request.answers()` (not `List.of()`).
- ✅ Controller tests: `PipelineControllerTest` (8 tests) — startPipeline, runPipeline with real team/answers, null safety, error paths, health check.
- ✅ Integration test: `IdeaPipelineIntegrationTest` (3 tests) — complete pipeline without loop, convergence loop, startPipeline.

### Result
The API represents the real pipeline with controller and integration test coverage.

---

## Recommended execution order

| # | Step | Block |
|---|------|--------|
| ✅ 1 | Documental/technical base alignment | Foundation |
| ✅ 2 | Prompt + implementation Phase 3a | Real convergence |
| ✅ 3 | Prompt + implementation Phase 3b | Real convergence |
| ✅ 4 | Prompt + implementation Phase 3.5 | Real architecture |
| ✅ 5 | Parseable specification of `tasks.md` | Foundation for Phase 4 |
| ✅ 6 | Prompt + implementation Phase 4 | Real planning |
| ✅ 7 | API adjustments + end-to-end integration | Closure |
| ✅ 8 | Final documentation review | Closure |

---

## ✅ Complete MVP (2026-05-25)

The 7 phases of the pipeline are implemented with real LLM logic:

| Phase | Tests | Status |
|------|-------|--------|
| 0 — Gatekeeper | 8 | ✅ |
| 1 — Debate | 7 | ✅ |
| 2 — Synthesis | 8 | ✅ |
| 3a — Critique | 10 | ✅ |
| 3b — Validation | 12 | ✅ |
| 3.5 — Stack Architecture | 11 | ✅ |
| 4 — Planning Poker | 14 | ✅ |
| PipelineControllerTest | 8 | ✅ |
| IdeaPipelineIntegrationTest | 3 | ✅ |
| LlmClientImpl | 6 | ✅ |
| **Total** | **87** | **Functional MVP** |

Pending for production: final docs review.

---

## Logical schedule by blocks

### Block A — Real convergence ✅
- Phase 3a (Critique) ✅
- Phase 3b (Validation) ✅

### Block B — Real architecture ✅
- Phase 3.5 (Stack Architecture) ✅

### Block C — Real executive planning ✅
- Harden `tasks.md` ✅
- Phase 4 (Planning Poker) ✅

### Block D — Technical product closure ✅
- API ✅
- Integration ✅
- Final documentation ✅

---

## MVP definition of done

### ✅ MVP definition of done — MET (2026-05-25)

- ✅ All 7 phases run real LLM logic.
- ✅ The convergence loop no longer depends on placeholders.
- ✅ `ArchitectureDoc` is generated from real context.
- ✅ `PlanningPokerOrchestrator` estimates real tasks parsed from hardened markdown.
- ✅ The API uses real data from the request (`team` and `answers`).
- ✅ There is unit + controller + integration test coverage (87 tests).
- ✅ End-to-end integration tests.
- ✅ The main documentation is aligned with the code.
