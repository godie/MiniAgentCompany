# PHASE-4-SPEC: Planning Poker (Task Estimation)

**Date:** 2026-05-25
**Version:** 1.0
**Status:** ✅ IMPLEMENTED

---

## 1. Objective

Transform `tasks.md` (Markdown document in hardened format with META blocks) + `ArchitectureDoc` + `Team` into a fully estimated `TaskGraph`. This is the final phase of the pipeline.

The phase has **two responsibilities**:
- **4A — Parsing**: Extract `Task` objects from the Markdown via LLM
- **4B — Planning Poker**: Estimate story points, per-member votes, dependencies and roles via LLM

---

## 2. Inputs

| Input | Type | Source | Description |
|-------|------|--------|-------------|
| `tasksMarkdown` | `String` | Phase 2 (Synthesis) | tasks.md document in hardened format (ADR-002) |
| `team` | `Team` | User request | Team with members, roles, seniority |
| `architecture` | `ArchitectureDoc` | Phase 3.5 | Stack, services, constraints, deployment |

---

## 3. tasks.md format (ADR-002)

The document follows a hardened format with:

- Sequential IDs: `EPIC-1`, `US-1`, `TASK-1`
- Blocks `<!-- META: {"id":"TASK-1","epicId":"EPIC-1","userStory":"US-1","dependencies":[]} -->`
- Fields: Description, Acceptance criteria, Estimate (S|M|L), Dependencies

See `docs/decisions/ADR-002-tasks-markdown-format.md` for the full canonical format.

---

## 4. Output: Phase 4A (Parsing)

`List<Task>` — extracted from the Markdown via LLM parser:

```json
{
  "tasks": [
    {
      "id": "TASK-1",
      "title": "Create registration endpoint",
      "description": "POST /api/auth/register",
      "epicId": "EPIC-1",
      "userStory": "US-1",
      "sizeEstimate": "M"
    }
  ],
  "epics": [{"id": "EPIC-1", "name": "Onboarding"}],
  "userStories": [{"id": "US-1", "title": "User Registration", "epicId": "EPIC-1"}]
}
```

> **Note:** The `sizeEstimate` field (S|M|L) is returned by the LLM parser as context for Phase 4B, but is NOT stored in the `Task` record (whose fields are: `id`, `title`, `description`, `epicId`, `userStory`). It is used only as a reference for the estimation.

---

## 5. Output: Phase 4B (Planning Poker)

`TaskGraph` — record with 4 fields:

```json
{
  "tasks": [
    {
      "taskId": "TASK-1",
      "storyPoints": 5,
      "votes": {"dev-1": 5, "dev-2": 8, "qa-1": 5},
      "dependencies": []
    }
  ],
  "adjacency": {"TASK-1": ["TASK-2"]},
  "totalPoints": 8,
  "pointsByRole": {"BACKEND_DEV": 5, "FULLSTACK_DEV": 3}
}
```

| Field | Type | Description |
|-------|------|-------------|
| `tasks` | `List<EstimatedTask>` | Estimated tasks with votes, storyPoints, dependencies |
| `adjacency` | `Map<String, List<String>>` | Dependency graph (DAG, no cycles) |
| `totalPoints` | `int` | Total sum of story points (cross-validated) |
| `pointsByRole` | `Map<String, Integer>` | Sum of story points grouped by `BaseRole` |

### Estimation rules

- **Fibonacci**: 1, 2, 3, 5, 8, 13, 21
- **Story points** = COMPLEXITY + EFFORT + UNCERTAINTY
- **Dependency**: TASK-A depends on TASK-B if it cannot START until TASK-B is DONE
- **Seniority**: Higher seniority → lower estimates
- **QA**: Estimates higher than devs (includes testing)
- **Consensus**: Final `storyPoints` = median of votes rounded to the nearest Fibonacci value

---

## 6. Behavior

### 6.1 Main flow

1. Validate inputs: `tasksMarkdown` not null, `team` not null/empty, `architecture` not null
2. **Phase 4A**: `parseTasksFromMarkdown(tasksMarkdown)` → `List<Task>`
   - If markdown is empty → empty `TaskGraph` early
   - Call the LLM parser with `TASK_PARSE_SYSTEM_PROMPT`
   - Extract `Task` objects from the JSON
3. **Phase 4B**: Planning Poker
   - Build `userMessage` with team, architecture, and the task list
   - Call the LLM with `PLANNING_POKER_SYSTEM_PROMPT`
   - Parse `EstimatedTask[]`, `adjacency`, `totalPoints`, `pointsByRole`
   - Cross-validate `totalPoints` against the computed sum (warning if mismatch)
   - Validate that every `taskId` in the response exists in `parsedTasks`
4. Return `TaskGraph`

### 6.2 System Prompts

- **Parser**: Acts as a precise parser. Extracts tasks, epics, user stories from the Markdown.
- **Planner**: Acts as a Scrum Master facilitating Planning Poker. Estimates from the perspective of EACH member of the team.

---

## 7. Error handling

| Condition | Exception | Phase |
|-----------|-----------|-------|
| `tasksMarkdown == null` | `PipelineException("tasksMarkdown is null", "Phase4", 0)` | Phase4 |
| `team == null \|\| team.members().isEmpty()` | `PipelineException("team is null or empty", "Phase4", 0)` | Phase4 |
| `architecture == null` | `PipelineException("architecture is null", "Phase4", 0)` | Phase4 |
| Empty markdown | Empty `TaskGraph` (no exception) | — |
| Empty parser response | `PipelineException("Empty response from task parser LLM", "Phase4", 0)` | Phase4 |
| Empty poker response | `PipelineException("Empty response from planning poker LLM", "Phase4", 0)` | Phase4 |
| Invalid JSON (parser) | `PipelineException("Failed to parse tasks from markdown", "Phase4", 0, cause)` | Phase4 |
| Invalid JSON (poker) | `PipelineException("Planning poker failed", "Phase4", 0, cause)` | Phase4 |
| Unknown `taskId` in response | `PipelineException("Unknown task ID in LLM response: X", "Phase4", 0)` | Phase4 |
| `totalPoints` mismatch | ⚠️ Warning log (no exception) | — |

---

## 8. Acceptance criteria

- [x] `PlanningPokerOrchestrator` uses `LlmClient` injected via constructor
- [x] No hardcoded tasks or estimates — everything from the LLM
- [x] Two system prompts as `private static final String`
- [x] `parseTasksFromMarkdown()` is a private method (separation of concerns)
- [x] Empty markdown → empty `TaskGraph` (no exception)
- [x] Team members, roles, seniority, extraResponsibilities in the prompt
- [x] Architecture (stack, services, constraints, deployment) in the prompt
- [x] Cross-validation of `totalPoints` with warning
- [x] Unknown IDs → `PipelineException`
- [x] Successful `mvn compile`
- [x] 14 unit tests pass (`PlanningPokerOrchestratorTest`)

---

## 9. Tests

See `PlanningPokerOrchestratorTest.java` (14 tests):

### Parser (4 tests)

| # | Test | What it verifies |
|---|------|------------------|
| 1 | `parseTasks_shouldExtractAllTasks` | Extracts 2 tasks from the Markdown |
| 2 | `parseTasks_shouldPreserveEpicAndUserStoryIds` | IDs EPIC-1, US-1, TASK-1 preserved |
| 3 | `parseTasks_shouldHandleEmptyMarkdown` | Empty string → empty TaskGraph, without calling the LLM |
| 4 | `parseTasks_shouldThrowPipelineException_onInvalidParseJson` | Invalid JSON from the parser → exception |

### Orchestrator (10 tests)

| # | Test | What it verifies |
|---|------|------------------|
| 5 | `runPlanningPoker_shouldReturnParsedTaskGraph` | Complete TaskGraph: tasks, adjacency, totalPoints, pointsByRole, votes |
| 6 | `runPlanningPoker_shouldThrowPipelineException_onInvalidJson` | Invalid JSON from poker → exception |
| 7 | `runPlanningPoker_shouldThrowPipelineException_onEmptyResponse` | Empty poker response → exception |
| 8 | `runPlanningPoker_shouldUseRealTeamMembers` | Names, roles, seniority in userMessage |
| 9 | `runPlanningPoker_shouldUseArchitectureDoc` | Stack, services, deployment, constraints in userMessage |
| 10 | `runPlanningPoker_shouldThrowPipelineException_onEmptyTeam` | Empty team → exception |
| 11 | `runPlanningPoker_shouldThrowPipelineException_onNullTasksMarkdown` | null markdown → exception |
| 12 | `runPlanningPoker_shouldThrowPipelineException_onUnknownTaskId` | TASK-999 does not exist → exception |
| 13 | `runPlanningPoker_shouldHandleMarkdownFencedResponse` | JSON inside ```json fences |
| 14 | `runPlanningPoker_shouldLogWarningOnTotalPointsMismatch` | totalPoints=100, sum=8 → warning, result returned |

---

## 10. Files involved

| File | Role |
|------|------|
| `PlanningPokerOrchestrator.java` | Implementation (2 LLM phases) |
| `PlanningPokerOrchestratorTest.java` | 14 unit tests |
| `TaskGraph.java` | Output record (unmodified) |
| `EstimatedTask.java` | Output record (unmodified) |
| `Task.java` | Internal record (unmodified) |
| `Team.java` / `TeamMember.java` | Input records (unmodified) |
| `ArchitectureDoc.java` | Input record (unmodified) |
| `ADR-002-tasks-markdown-format.md` | ADR of the parseable format |

---

*End of spec.*
