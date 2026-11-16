# IdeaPipeline Project Review

**Date:** 2026-05-25
**Version:** 1.1

## 1. Executive summary

The project has a solid functional base for all phases of the pipeline:

- **Phase 0 (Gatekeeper)** implemented with real calls to the LLM.
- **Phase 1 (Debate)** implemented with parallelism using `StructuredTaskScope`.
- **Phase 2 (Synthesis)** implemented with parallel document generation.
- **LlmClient / LlmClientImpl** implemented.
- **IdeaPipeline** implemented with a convergence loop.
- **PipelineController** exposed via REST.

**The pipeline is complete end to end.** All 7 phases run real logic with the LLM, the API exposes the pipeline with real request data, and there are 87 unit + controller + integration tests passing. The mismatches between documentation, configuration and API contracts have been resolved.

---

## 2. Current strengths

### 2.1 Correct technical base
- Use of **Java 21**.
- Modern parallelism with **virtual threads** and **StructuredTaskScope**.
- Reasonable separation between `client`, `orchestrator`, `pipeline`, `controller`, `model`.
- Data contracts defined with **records**.

### 2.2 Well-documented pipeline design
The files `idea.md`, `META_PIPELINE.md` and `AGENTS.md` make clear:
- the purpose of the system,
- the phases,
- the expected outputs,
- the convergence logic.

### 2.3 Good starting point for TDD
Tests already exist for:
- `LlmClientImpl`
- `GatekeeperOrchestrator`
- `DebateOrchestrator`
- `SynthesisOrchestrator`

This allows extending the project with an incremental and verifiable strategy.

---

## 3. Gaps and risks detected

### 3.1 Final phases — updated state (2026-05-24)

| Phase | Class | Current state | Tests |
|------|-------|---------------|-------|
| 3a | `CriticOrchestrator` | ✅ Implemented — calls the LLM, constructor injection, token budget | 10 |
| 3b | `ValidationOrchestrator` | ✅ Implemented — LLM rubric, no dummy formula, range validation | 12 |
| 3.5 | `StackArchitectOrchestrator` | ✅ Implemented — senior LLM architect, validation of required fields | 11 |
| 4 | `PlanningPokerOrchestrator` | ✅ Implemented — 2 LLM phases (parsing + planning poker), cross-validation of totalPoints | 14 |

**Progress:** The 4 final phases implemented (47 new tests). Complete MVP.

**Impact:**
- The pipeline produces real convergence based on the LLM.
- The resulting architecture is tied to the generated context.
- Planning poker transforms the hardened `tasks.md` into a `TaskGraph` with estimates, dependencies and votes per team member.

### 3.2 Mismatches between documentation and code ✅ RESOLVED (2026-05-24)

| Document said | Code/Config had | Action |
|----------------|---------------------|--------|
| OpenAI / gpt-4o-mini as provider | Ollama as provider (qwen3.5:4b) | Docs updated to Ollama/qwen3.5:4b |
| `CONTEXT.md` as canonical file | The actual file was named `CONTEX.md` | Renamed to `CONTEXT.md` |
| Spring Boot 3.3 in CONTEX.md | Spring Boot 4.1.0-SNAPSHOT in pom.xml | Updated to 4.1.0-SNAPSHOT |
| Spring AI 0.8.1 in docs | Spring AI 2.0.0-SNAPSHOT in pom.xml | Updated to 2.0.0-SNAPSHOT |

**Impact (resolved):**
- Documentation and code are now aligned.
- ADR-001 documents the LLM provider decision.

### 3.3 Functional gap in the API — RESOLVED (2026-05-25)
`PipelineController` fixed and covered by tests:

- ✅ `runPipeline()` uses the real `request.answers()` (with a safe fallback).
- ✅ `runPipeline()` uses the real `request.team()` (with fallback to default).
- ✅ `RunRequest` includes the `List<RefinementQA> answers` field.
- ✅ Controller tests: `PipelineControllerTest` (8 tests).
- ✅ Integration test: `IdeaPipelineIntegrationTest` (3 tests — complete pipeline, convergence loop, startPipeline).

### 3.4 Parsing risk for Phase 4 ✅ RESOLVED (2026-05-24)
Phase 4 needs to transform `tasks.md` into `List<Task>`.

**Solution applied:**
- Hardened format with sequential IDs (`EPIC-1`, `US-1`, `TASK-1`) and `<!-- META: {...} -->` blocks.
- ADR-002 documents the canonical format.
- `PlanningPokerOrchestrator.parseTasksFromMarkdown()` parses via the LLM with a dedicated system prompt.

### 3.5 Testing coverage — updated (2026-05-25)

| Level | Status |
|-------|--------|
| Unit tests — phases 0, 1, 2 | ✅ 23 tests |
| Unit tests — phases 3a, 3b, 3.5, 4 | ✅ 47 tests |
| Unit tests — `LlmClientImpl` | ✅ 6 tests |
| Controller tests — `PipelineControllerTest` | ✅ 8 tests |
| Integration tests — `IdeaPipelineIntegrationTest` | ✅ 3 tests |
| **Total** | **87 tests** |

### 3.6 Snapshot dependency
The project uses snapshot versions of Spring Boot and Spring AI.

**Impact:** It may introduce instability or API changes. It is advisable to monitor compatibility while the MVP is completed.

---

## 4. Review conclusion

### General state
The project has reached the **functional MVP** — all 7 phases of the pipeline run real logic with the LLM.

### Real priority
The priority now is:
1. ~~Complete phases 3a, 3b, 3.5 and 4.~~ ✅ Completed
2. ~~Align documentation + configuration + contracts.~~ ✅ Completed
3. ~~Close the end-to-end flow from API to final outputs.~~ ✅ Completed (API + controller tests + integration)
4. ~~Add integration and controller tests.~~ ✅ Completed (11 new tests)
5. Final documentation review.

### Main recommendation
The next stretch should run in this order:

1. ~~**Align the documental base and contracts.**~~ ✅
2. ~~**Implement 3a and 3b** to have real convergence.~~ ✅
3. ~~**Implement 3.5** to generate real architecture.~~ ✅
4. ~~**Harden the `tasks.md` format** for safe parsing.~~ ✅
5. ~~**Implement Phase 4.**~~ ✅
6. ~~**Close the API and end-to-end tests.**~~ ✅7. **Final documentation review.** 🔄
