# Detailed plan by phase: 3a, 3b, 3.5 and 4

**Date:** 2026-05-25
**Version:** 1.1

---

## Phase 3a — Critique ✅ IMPLEMENTED (2026-05-24)

### Objective
Audit `context.md`, `flows.md` and `tasks.md` to detect inconsistencies, gaps and points that should go back to the debate.

### Expected output (JSON)
```json
{
  "critiques": ["crítica 1", "crítica 2"],
  "gaps": ["gap 1"],
  "refinementNeeds": ["tema a re-debatir 1"]
}
```

### Prompt to create
`prompts/phase-3a-critique.prompt.md`

### Suggested system prompt
> You are a product and architecture auditor. Your job is to analyze three documents — context, flows and tasks — and find inconsistencies, gaps, risks and topics that need more debate. You are not negative: you are rigorous. Separate your findings into critiques (problems found), gaps (what is missing) and refinementNeeds (specific topics that should return to the debate).

### Expected implementation
- Inject `LlmClient` into `CriticOrchestrator`.
- Build the `systemPrompt` for document auditing.
- Build the `userMessage` with the 3 docs and optionally a summarized history.
- Parse the JSON with `JsonParser.parse()`.
- Handle empty responses/invalid JSON with `PipelineException("Phase3a", loopCount)`.

### Minimum tests
1. `critique_shouldReturnParsedCritiqueResult`
2. `critique_shouldThrowPipelineException_onInvalidJson`
3. `critique_shouldThrowPipelineException_onEmptyResponse`
4. `critique_shouldHandleMarkdownFencedResponse`
5. `critique_shouldIncludeAllThreeDocumentsInPrompt`

### Document state on entry
The 3 documents come from `PipelineOutput` (Phase 2), in free-form Markdown.

---

## Phase 3b — Validation ✅ IMPLEMENTED (2026-05-24)

### Objective
Decide whether the pipeline converges or needs a loop, based on a real LLM evaluation.

### Expected output (JSON)
```json
{
  "convergenceScore": 82,
  "shouldLoop": false,
  "reasoning": "Los documentos son coherentes entre sí. No hay gaps bloqueantes."
}
```

### Convergence scale
| Range | Meaning |
|-------|-------------|
| 0–40 | Loop mandatory — major inconsistencies |
| 41–74 | Loop recommended — significant gaps |
| 75–100 | Proceed — documents sufficiently coherent |

### Prompt to create
`prompts/phase-3b-validation.prompt.md`

### Suggested system prompt
> You are a convergence evaluator for a software design pipeline. Your job is to read three documents (context, flows, tasks) and the result of a prior audit, and decide whether the documents are sufficiently coherent to proceed. Assign a convergenceScore from 0-100. Answer ONLY in JSON.

### Expected implementation
- Inject `LlmClient` into `ValidationOrchestrator`.
- Send the LLM: the 3 docs + `CritiqueResult` + `loopCount`.
- Parse the JSON into `ValidationResult`.
- Do not mix the `maxLoops` policy in here; that stays in `IdeaPipeline`.

### Minimum tests
1. `validate_shouldReturnParsedValidationResult`
2. `validate_shouldThrowPipelineException_onInvalidJson`
3. `validate_shouldThrowPipelineException_onScoreOutOfRange`
4. `validate_shouldIncludeLoopCountInPrompt`
5. `validate_shouldNotUseDummyFormula` — verifies that the score is not `100 - loopCount*5`

---

## Phase 3.5 — Stack Architecture ✅ IMPLEMENTED (2026-05-24)

### Objective
Define the stack, services, constraints and deployment model based on converged documents.

### Expected output (JSON — recommended for robustness)
```json
{
  "stack": "Java 21 + Spring Boot + PostgreSQL + Redis",
  "services": ["API Gateway", "Auth Service", "Core Service", "Notification Worker"],
  "constraints": ["Debe correr en Kubernetes", "Latencia < 200ms p95"],
  "deploymentModel": "Microservicios en Kubernetes con Istio",
  "rationale": "Spring Boot por familiaridad del equipo. PostgreSQL por consistencia ACID. Redis para caching de sesiones."
}
```

### Prompt to create
`prompts/phase-3.5-stack-architecture.prompt.md`

### Suggested system prompt
> You are a senior Software Architect. Based on the context, flows and tasks documents of a software project, define the complete technology stack. Consider scalability, team complexity, time-to-market and costs. Justify every decision. Answer ONLY in JSON.

### Expected implementation
- Inject `LlmClient`.
- Send `IdeaContext` + `PipelineOutput` + debate points.
- Parse the JSON into an `ArchitectureDoc` record.
- Handle null/blank/invalid JSON.

### Minimum tests
1. `defineStack_shouldReturnParsedArchitectureDoc`
2. `defineStack_shouldThrowPipelineException_onEmptyResponse`
3. `defineStack_shouldThrowPipelineException_onInvalidJson`
4. `defineStack_shouldIncludeAllDocumentsInPrompt`

---

## Phase 4 — Planning Poker ✅ IMPLEMENTED (2026-05-25)

### Objective
Transform `tasks.md` + `ArchitectureDoc` + `Team` into an estimated `TaskGraph`.

### Important prior decision
Before implementing, fix the **parseable format of `tasks.md`** (see Priority 4 in PLAN_SIGUIENTES_PASOS.md).

### Design in two responsibilities

#### 4A. Parsing markdown into `Task`
- Read `tasks.md`.
- Extract epics (EPIC-X), user stories (US-X), tasks (TASK-X).
- Extract IDs, dependencies and metadata from the `<!-- META: {...} -->` block.

#### 4B. Estimation with LLM
- Use `ArchitectureDoc` + `Team` + `List<Task>` as input.
- Return story points, simulated votes per role and consolidated dependencies.

### Expected output (JSON)
```json
{
  "tasks": [
    {
      "task": { "id": "TASK-1", "title": "Crear endpoint de registro", "description": "...", "epicId": "EPIC-1", "userStory": "US-1" },
      "storyPoints": 5,
      "votes": { "BACKEND_DEV": 5, "QA_ENGINEER": 3 },
      "dependencies": ["TASK-0"]
    }
  ],
  "adjacency": { "TASK-0": ["TASK-1"] },
  "totalPoints": 21,
  "pointsByRole": { "BACKEND_DEV": 13, "QA_ENGINEER": 8 }
}
```

### Prompt to create
`prompts/phase-4-planning-poker.prompt.md`

### Suggested system prompt
> You are a Scrum Master who knows the stack and the team. Your job is to estimate a set of tasks using simulated Planning Poker. For each task, assign story points (Fibonacci: 1,2,3,5,8,13) from the perspective of each team role. Identify dependencies between tasks. Answer ONLY in JSON with the TaskGraph structure.

### Expected implementation
- Add method `parseTasksFromMarkdown(String markdown)`.
- Define the Scrum Master / simulated team prompt.
- Parse the JSON into `TaskGraph`.
- Validate ID and dependency consistency.
- Calculate or validate `totalPoints` and `pointsByRole`.

### Minimum tests

#### Parser
1. `parseTasks_shouldExtractAllTasks`
2. `parseTasks_shouldExtractDependencies`
3. `parseTasks_shouldHandleEmptyMarkdown`
4. `parseTasks_shouldPreserveEpicAndUserStoryIds`

#### Orchestrator
1. `runPlanningPoker_shouldReturnParsedTaskGraph`
2. `runPlanningPoker_shouldThrowPipelineException_onInvalidJson`
3. `runPlanningPoker_shouldThrowPipelineException_onEmptyResponse`
4. `runPlanningPoker_shouldUseRealTeamMembers`
5. `runPlanningPoker_shouldUseArchitectureDoc`

---

## Summary of files created/modified per phase

| Phase | Prompt (.md) | Code | Tests | Status |
|------|-------------|--------|-------|--------|
| 3a | `prompts/phase-3a-critique.prompt.md` | `CriticOrchestrator.java` | `CriticOrchestratorTest.java` (10 tests) | ✅ |
| 3b | `prompts/phase-3b-validation.prompt.md` | `ValidationOrchestrator.java` | `ValidationOrchestratorTest.java` (12 tests) | ✅ |
| 3.5 | `prompts/phase-3.5-stack-architecture.prompt.md` | `StackArchitectOrchestrator.java` | `StackArchitectOrchestratorTest.java` (11 tests) | ✅ |
| 4 | `prompts/phase-4-planning-poker.prompt.md` | `PlanningPokerOrchestrator.java` | `PlanningPokerOrchestratorTest.java` (14 tests) | ✅ |
