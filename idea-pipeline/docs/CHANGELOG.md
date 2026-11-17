# CHANGELOG

**Changelog version:** 1.0  
**Project:** Idea Pipeline  
**Last entry date:** 2026-05-25  

Chronological record of all implemented phases of the pipeline, from the base alignment to the MVP closure.  
Based on the reports in `docs/reports/` and the plan `docs/PLAN_SIGUIENTES_PASOS.md`.

---

## Before 2026-05-24 — Pipeline foundation

### Phases 0–2 + Infrastructure (pre-report tracking)

**What existed before starting report tracking:**

- `GatekeeperOrchestrator` — Phase 0: refinement questions + context enrichment (8 tests)
- `DebateOrchestrator` — Phase 1: multi-agent debate with `StructuredTaskScope` (7 tests)
- `SynthesisOrchestrator` — Phase 2: parallel synthesis of 3 documents (8 tests)
- `LlmClientImpl` — LLM client with `chat()` and `chatWithHistory()` (6 tests)
- `JsonParser` — Tolerant JSON extraction from Markdown responses
- `PipelineProperties` + `PipelineConfig` — YAML configuration and Spring beans
- `PipelineException` — Checked exception with phase and loop metadata
- Data models (`RawIdea`, `IdeaContext`, `DebateMessage`, `PipelineOutput`, `TaskGraph`, etc.)
- `IdeaPipeline` — Central orchestrator with convergence loop (placeholders in phases 3a–4)

**Tests:** 29/29 ✅

---

## 2026-05-24 — Block A: Real convergence

### Phase 3a — Critique (2026-05-24)

**Report:** [`docs/reports/PHASE-3A-IMPLEMENTATION-REPORT.md`](reports/PHASE-3A-IMPLEMENTATION-REPORT.md)  
**Spec:** [`docs/phases/PHASE-3A-SPEC.md`](phases/PHASE-3A-SPEC.md)  
**Prompt:** [`prompts/phase-3a-critique.prompt.md`](../prompts/phase-3a-critique.prompt.md)

**What changed:** The hardcoded `CritiqueResult` was replaced with a real LLM-based audit.

- `CriticOrchestrator.java` — rewritten with `LlmClient` injection, auditor system prompt, token budget (5 + 10 over 30 messages), tolerant `extractStringList()`
- `CriticOrchestratorTest.java` — 10 tests (parsing, errors, markdown fences, truncation, missing fields)
- Result: real `CritiqueResult` with `critiques`, `gaps`, `refinementNeeds` from the LLM

**Tests:** 39/39 (29 previous + 10 new) ✅

---

### Phase 3b — Validation (2026-05-24)

**Report:** [`docs/reports/PHASE-3B-IMPLEMENTATION-REPORT.md`](reports/PHASE-3B-IMPLEMENTATION-REPORT.md)  
**Spec:** [`docs/phases/PHASE-3B-SPEC.md`](phases/PHASE-3B-SPEC.md)  
**Prompt:** [`prompts/phase-3b-validation.prompt.md`](../prompts/phase-3b-validation.prompt.md)

**What changed:** The dummy formula `score = max(10, 100 - loopCount * 5)` was removed and real evaluation with a 0–100 rubric was implemented.

- `ValidationOrchestrator.java` — rewritten with a 4-level rubric, explicit `loopCount`, range validation after parsing
- `ValidationOrchestratorTest.java` — 12 tests (low score → loop, score out of range, verification that `chat()` is called, markdown fences)
- `maxLoops` is kept as policy in `IdeaPipeline`, not in the orchestrator
- Result: `ValidationResult` coherent with the documents and the previous critique

**Tests:** 51/51 (39 previous + 12 new) ✅

---

## 2026-05-24 — Block B: Real architecture

### Phase 3.5 — Stack Architecture (2026-05-24)

**Report:** [`docs/reports/PHASE-3.5-IMPLEMENTATION-REPORT.md`](reports/PHASE-3.5-IMPLEMENTATION-REPORT.md)  
**Spec:** [`docs/phases/PHASE-3.5-SPEC.md`](phases/PHASE-3.5-SPEC.md)  
**Prompt:** [`prompts/phase-3.5-stack-architecture.prompt.md`](../prompts/phase-3.5-stack-architecture.prompt.md)

**What changed:** The hardcoded `ArchitectureDoc` was replaced with a real stack definition via LLM.

- `StackArchitectOrchestrator.java` — rewritten with a senior architect system prompt, 5 fields, validation of required `stack` + `services`, optional `constraints`
- `StackArchitectOrchestratorTest.java` — 11 tests (5 fields, errors, markdown fences, empty stack/services, missing constraints)
- Result: `ArchitectureDoc` with stack, services, constraints, deployment and rationale

**Tests:** 62/62 (51 previous + 11 new) ✅

---

## 2026-05-24 — Foundation for Phase 4

### ADR-002: Canonical `tasks.md` format (2026-05-24)

**ADR:** [`docs/decisions/ADR-002-tasks-markdown-format.md`](decisions/ADR-002-tasks-markdown-format.md)

**What changed:** The `tasks.md` format was hardened to make it robustly parseable:

- Sequential IDs: `EPIC-1`, `US-1`, `TASK-1`
- Metadata blocks: `<!-- META: {"id":"TASK-1","epicId":"EPIC-1","dependencies":["TASK-2"]} -->`
- Explicit `Dependencies` field
- `TASK_DOC_PROMPT` in `SynthesisOrchestrator.java` updated with a complete example

**Reason:** The Phase 4 parser needs stable IDs and explicit dependencies; without this format, parsing would be fragile.

---

## 2026-05-25 — Block C: Real executive planning

### Phase 4 — Planning Poker (2026-05-25)

**Report:** [`docs/reports/PHASE-4-IMPLEMENTATION-REPORT.md`](reports/PHASE-4-IMPLEMENTATION-REPORT.md)  
**Spec:** [`docs/phases/PHASE-4-SPEC.md`](phases/PHASE-4-SPEC.md)  
**Prompt:** [`prompts/phase-4-planning-poker.prompt.md`](../prompts/phase-4-planning-poker.prompt.md)  
**Prerequisite:** [`docs/decisions/ADR-002-tasks-markdown-format.md`](decisions/ADR-002-tasks-markdown-format.md)

**What changed:** The hardcoded `TaskGraph` was replaced with real planning using two consecutive LLM calls.

- `PlanningPokerOrchestrator.java` — rewritten with two phases:
  - **Phase 4A:** `parseTasksFromMarkdown()` — LLM parser from Markdown to JSON
  - **Phase 4B:** `buildPlanningPokerUserMessage()` — Scrum Master simulates Planning Poker with Fibonacci
- Cross-validation of `totalPoints` (non-blocking warning)
- Validation of `taskId` against parsed tasks
- Empty Markdown → empty `TaskGraph` (no exception)
- `PlanningPokerOrchestratorTest.java` — 14 tests (4 parser + 10 orchestrator)
- Result: `TaskGraph` with Fibonacci estimates, dependencies, votes, points per role

**Tests:** 76/76 (62 previous + 14 new) ✅

---

## 2026-05-25 — Block D: Technical product closure

### Controller + Integration (2026-05-25)

**What changed:** The gap between the API and the real pipeline was closed.

- `RunRequest` extended with an `answers` field (`List<RefinementQA>`)
- `PipelineController` modified to use real `request.team()` and `request.answers()` (not dummies)
- `PipelineControllerTest.java` — 8 tests (startPipeline, runPipeline with real team/answers + null safety + error, health check)
- `IdeaPipelineIntegrationTest.java` — 3 @SpringBootTest tests (full pipeline without loop, convergence loop, startPipeline)

**Tests:** **87/87** ✅ (76 previous + 11 new)

---

### Post-MVP documentation (2026-05-25)

**What changed:** Consistency review across 7 documentation files.

- `META_PIPELINE.md` — v1.4→v1.5, section 11 date → 2026-05-25
- `CONTEXT.md` — section 10 rewritten (from "proof of concept" to MVP table with 87 tests)
- `AGENTS.md` — date → 2026-05-25
- `REVISION_PROYECTO.md` — section 1 rewritten (from "pipeline not complete" to "complete end-to-end")
- `PLAN_SIGUIENTES_PASOS.md` — step 8 ✅, Block D ✅
- `WORKFLOW_PROMPTS_IMPLEMENTACION.md` — docs tree updated, all steps ✅
- `PLAN_FASES_3A_4.md` — Phase 4 ⏳→✅ with label IMPLEMENTADO

---

### Phase documentation (2026-05-25)

**What changed:** Specs and reports were created for all phases 3a–4.

- `docs/phases/PHASE-3A-SPEC.md` — detailed specification
- `docs/phases/PHASE-3B-SPEC.md` — detailed specification
- `docs/phases/PHASE-3.5-SPEC.md` — detailed specification
- `docs/phases/PHASE-4-SPEC.md` — detailed specification
- `docs/reports/PHASE-3A-IMPLEMENTATION-REPORT.md` — implementation report
- `docs/reports/PHASE-3B-IMPLEMENTATION-REPORT.md` — implementation report
- `docs/reports/PHASE-3.5-IMPLEMENTATION-REPORT.md` — implementation report
- `docs/reports/PHASE-4-IMPLEMENTATION-REPORT.md` — implementation report

---

### Javadoc (2026-05-25)

**What changed:** Javadoc was added to 13 public pipeline classes.

- 7 orchestrators, `LlmClientImpl`, `IdeaPipeline`, `PipelineController`, `PipelineConfig`, `PipelineProperties`, `PipelineException`
- `@param`, `@return`, `@throws`, `{@link}` cross-references between phases

---

### README.md (2026-05-25)

**What changed:** The project `README.md` was created with quick start, API reference, configuration, phases and contribution guide.

---

### CHANGELOG.md (2026-05-25)

**This file** — consolidated chronological record.

---

## MVP Summary

| Date | Milestone | Cumulative tests |
|-------|------|:---:|
| < 2026-05-24 | Phases 0–2 + LlmClient (foundational phase) | 29 |
| 2026-05-24 | Phase 3a — Critique | 39 |
| 2026-05-24 | Phase 3b — Validation | 51 |
| 2026-05-24 | Phase 3.5 — Stack Architecture | 62 |
| 2026-05-24 | ADR-002 — `tasks.md` format | 62 |
| 2026-05-25 | Phase 4 — Planning Poker | 76 |
| 2026-05-25 | Controller + Integration | **87** |
| 2026-05-25 | Post-MVP documentation, Javadoc, README | **87** |

**Complete MVP:** All 7 pipeline phases implemented with real LLM logic. Functional convergence loop. End-to-end API. 87 tests passing.

---

*End of changelog.*
