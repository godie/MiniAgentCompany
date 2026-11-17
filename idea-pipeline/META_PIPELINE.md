# META_PIPELINE.md — Debate and Convergence Pipeline between LLMs

**Version:** 1.4
**Last updated:** 2026-04-19
**Based on:** manually converged design (score 93) and Engineer Lead prompt v1.1

---

## 1. Purpose of the document

This file is the **source of truth** for the multi-agent system. It defines:

- The **Generative vs Executive** duality of the pipeline.
- The phases and their flow.
- The **canonical exchange format** between agents (version 1.0).
- The roles and responsibilities of each LLM.
- The convergence and exit criteria.

---

## 2. Fundamental duality: Generative vs Executive

| Nature | Phases | Objective | Parallelism | Planning |
|------------|-------|----------|-------------|----------------|
| **Generative** | 0, 1, 2, 3a, 3b, 3.5 | Refine an idea until coherent documents and a technical stack are obtained. | Low (sequential debate, synthesis in parallel only in automatic mode). | Iterates until convergence (max. 3 loops). |
| **Executive** | 4 | Generate concrete tasks, estimates and assignments for coding agents. | High (team simulation, planning poker). | Runs once, on already converged documents. |

**Golden rule:**
> Do not jump from phase 3.5 to 4 while `convergenceScore` < 75 or the maximum number of loops has not been exhausted.

---

## 3. Pipeline phases

| Phase | Name | Nature | Input | Output | Exit criterion |
|------|--------|------------|-------|--------|--------------------|
| 0 | Gatekeeper | Generative | `RawIdea` | Enriched `IdeaContext` | User answers the refinement questions |
| 1 | Debate | Generative | `IdeaContext` + empty history | `List<DebateMessage>` (rounds) | Fixed number of rounds (e.g. 2‑3) or exhaustion of gaps |
| 2 | Document Synthesis | Generative | Debate history + `IdeaContext` | 3 docs: `context.md`, `flows.md`, `tasks.md` | Documents generated (no validation yet) |
| 3a | Critique | Generative | The 3 docs + history | `CritiqueResult` (critiques, gaps, refinementNeeds) | Critiques generated |
| 3b | Validation + Convergence | Generative | The 3 docs + `CritiqueResult` + `loopCount` | `ValidationResult` (convergenceScore, shouldLoop, reasoning) | `convergenceScore >= 75` or max 3 loops |
| 3.5 | Stack Architecture | Generative | `IdeaContext` + 3 docs + history | `ArchitectureDoc` | Document generated |
| 4 | Planning Poker | Executive | `tasks.md` + `ArchitectureDoc` + `Team` | `TaskGraph` | All tasks estimated and graph generated |

---

## 4. Data models (Java 21 records)

### Idea domain
- `RawIdea(String description)`
- `RefinementQA(String question, String answer)`
- `IdeaContext(String rawIdea, List<RefinementQA> refinements, String enrichedSummary)`

### Debate domain
- `DebateMessage(String agentName, String content, int round)`
- `PipelineOutput(String contextDocument, String flowDocument, String taskDocument)`

### Validation domain (separate)
- `CritiqueResult(List<String> critiques, List<String> gaps, List<String> refinementNeeds)`
- `ValidationResult(int convergenceScore, boolean shouldLoop, String reasoning)`

### Architecture domain
- `ArchitectureDoc(String stack, List<String> services, List<String> constraints, String deploymentModel, String rationale)`

### Team domain
- `TeamMember(String id, String name, BaseRole baseRole, Seniority seniority, List<String> extraResponsibilities)`
- `Team(List<TeamMember> members)` — helpers: `byRole(BaseRole)`, `leads()`
- `BaseRole` enum: FRONTEND_DEV, BACKEND_DEV, FULLSTACK_DEV, QA_ENGINEER, DEVOPS, MOBILE_DEV
- `Seniority` enum: JUNIOR, MID, SENIOR, LEAD, PRINCIPAL

### Planning domain
- `Task(String id, String title, String description, String epicId, String userStory)`
- `EstimatedTask(Task task, int storyPoints, Map<String, Integer> votes, List<String> dependencies)`
- `TaskGraph(List<EstimatedTask> tasks, Map<String, List<String>> adjacency, int totalPoints, Map<String, Integer> pointsByRole)`

### Job tracking domain
- `PipelineJobStatus` enum: QUEUED, PROCESSING, DONE, ERROR
- `PipelineJob` entity: id (UUID), status, createdAt, updatedAt, errorMessage, resultJson
- `PipelineJobRepository` extends `JpaRepository<PipelineJob, String>`

### REST API layer
- `PipelineController` — `/pipeline` endpoints
- `PipelineJobService` — async job execution with `@Async`
- DTOs: `QuestionsRequest`, `QuestionsResponse`, `RunPipelineRequest`, `RunPipelineResponse`, `JobStatusResponse`, `JobResultResponse`

### CLI layer
- `PipelineRunner` — `ApplicationRunner` with `--idea`, `--team`, `--interactive`, `--refinements`, `--output` flags
- `TaskParser` — static parser for task markdown documents

### Pipeline state & result
- `PipelineState(IdeaContext context, List<DebateMessage> debateHistory, PipelineOutput output, int loopCount)`
- `PipelineResult(IdeaContext context, PipelineOutput documents, ArchitectureDoc architecture, TaskGraph taskGraph, int loopsRequired)`

---

## 5. Canonical exchange format (v1.0)

All communication between phases must follow this template:

```
---
PHASE: [number and name]
AGENT: [role of the receiving agent]
FROM: [role of the sending agent]
---

SUMMARY:
[2-5 lines max — what was decided/generated in the previous phase]

CONTEXT_FILES:
[list of reference files, their content is not repeated]

INPUT:
[the concrete data this agent needs to work]

TASK:
[clear, single instruction — what it must produce]

OUTPUT_FORMAT:
[exact expected format: JSON / Markdown / record]
---
```

**Additional rules:**
- The files listed in `CONTEXT_FILES` must be available locally.
- If `INPUT` is too long, truncate with a summary and the note `[TRUNCATED – see history file.txt]`.
- For long documents, use tags such as `<!-- CONTEXT_DOC --> ... <!-- END_CONTEXT_DOC -->`.

---

## 6. Roles in execution (manual test)

| Role | LLM Agent | Responsibility |
|-----|------------|-----------------|
| **Orchestrator / PO** | Human | Holds the idea, decides what advances, copies context, evaluates outputs, gives the go/no‑go. |
| **Architect + Gatekeeper** | Claude | Architecture design, prompt generation, refinement with questions. |
| **CriticAgent (Phase 3a)** | DeepSeek | Reviews prompts, documents and decisions looking for gaps, problems and risks. |
| **ValidationAgent (Phase 3b)** | ChatGPT | Takes the critic's output + the documents and gives the convergence verdict. |

> In phases 1 and 2, the ProductAgent, ArchitectAgent and CriticAgent roles are played by the same LLMs according to the system prompt. In phase 4, the Scrum Master LLM can be any (recommended: Claude).

---

## 7. Convergence criteria

- **Design convergence** (of the pipeline): `convergenceScore >= 80` and `shouldLoop = false`.
- **Instance convergence** (for a specific idea): `convergenceScore >= 75`.

**Maximum loops in phase 3b:** 3. If after 3 loops the score is still <75, the pipeline stops and reports "forced convergence".

**`convergenceScore` formula (guideline):**
- 0–40: loop mandatory (major inconsistencies)
- 41–74: loop recommended (significant gaps)
- 75–100: advance (sufficiently coherent documents)

---

## 8. Shared context files

| File | Purpose |
|---------|-----------|
| `META_PIPELINE.md` | This file — source of truth for the pipeline and canonical format |
| `CONTEXT.md` | General project description, stack, design decisions |
| `AGENTS.md` | Definition of each agent, its personality and system prompt |
| `idea.md` | The specific idea being refined |
| `log.md` | Log of decisions by the human orchestrator |

---

## 9. Step-by-step manual workflow

1. **Start** – The Orchestrator writes a `RawIdea` in `idea.md`.
2. **Phase 0** – Copies `idea.md` to Claude with the Gatekeeper prompt. Claude returns questions. The Orchestrator answers.
3. **Phase 1** – The Orchestrator sends the enriched `IdeaContext` and the history to the three LLMs in parallel, using the canonical format. Collects responses, consolidates and repeats rounds.
4. **Phase 2** – Sends the complete history to a SynthesisAgent (Claude or ChatGPT) to generate the 3 docs.
5. **Phase 3a** – Passes the 3 docs to DeepSeek (CriticAgent) to obtain `CritiqueResult`.
6. **Phase 3b** – Passes the 3 docs + `CritiqueResult` to ChatGPT (ValidationAgent) to obtain `ValidationResult` and the loop decision.
7. **If loop** – Enriches `IdeaContext` with the `gaps` and `refinementNeeds` and returns to Phase 1.
8. **If converged** – Advances to Phase 3.5 (Stack Architecture with Claude).
9. **Phase 4** – Configures the team (`team.json` file) and runs Planning Poker with a Scrum Master LLM on `tasks.md`.

---

## 10. Key design decisions

- **Sequential implementation (v1)**: IdeaPipeline runs phases in order. Functional base for the proof of concept.
- **Virtual threads + StructuredTaskScope (pending — v2)**: cleaner, automatic failure handling with `ShutdownOnFailure`. Not implemented in v1.
- **Stack defined before Planning Poker**: avoids vague estimates.
- **Two signatures in `LlmClient`**: `chat()` for phases without history, `chatWithHistory()` for debate.
- **Critique and Validation separation**: different roles and models (DeepSeek critiques, ChatGPT validates).
- **Simplified Planning Poker**: a single Scrum Master LLM simulates the whole team; avoids hundreds of calls.
- **Canonical exchange format**: ensures reproducibility in the manual test.

---

## 11. Implementation status

- ✅ `META_PIPELINE.md` (v1.4) — updated.
- ✅ `idea.md` — approved (score 96).
- ✅ `CONTEXT.md` (v1.3) — updated.
- ✅ `AGENTS.md` (v1.2) — updated.
- ✅ All orchestrators — implemented, 85 tests passing.
- ✅ `IdeaPipeline.java` — implemented, 96 tests passing.
- ✅ `PipelineRunner` (CLI) — implemented, 106 tests total.
- ✅ REST API — implemented, 129 tests total.
  - `PipelineJob` entity + repository
  - `PipelineJobService` (async execution)
  - `PipelineController` (5 endpoints)
  - DTOs (6 records)
- ✅ `@EnableAsync` in `PipelineConfig`
- ⏳ Virtual threads + StructuredTaskScope — pending (v2).

---

*End of document.*
```

---
