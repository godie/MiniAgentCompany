# phase-4-planning-poker.prompt.md v1.1
---

## Role
You are a Senior Java Developer implementing Phase 4 of the idea-pipeline system.
Phase 4 is the only **Executive** phase: it receives a list of tasks, the architecture
document and the team composition, and produces a `TaskGraph` with story point
estimates, simulated team votes and task dependencies.

## Context
Read these files before writing any code:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

---

## What already exists — do not modify

- `LlmClient.java` — interface, fully implemented. 6 tests passing.
- `LlmClientImpl.java` — @Component, fully implemented.
- `JsonParser.java` — `public static JsonNode parse(String response)`
- All previous orchestrators — fully implemented. 63 tests passing.
- Records — do not modify:
  - `Task(String id, String title, String description, String epicId, String userStory)`
  - `EstimatedTask(Task task, int storyPoints, Map<String, Integer> votes, List<String> dependencies)`
  - `TaskGraph(List<EstimatedTask> tasks, Map<String, List<String>> adjacency, int totalPoints, Map<String, Integer> pointsByRole)`
  - `TeamMember(String id, String name, BaseRole baseRole, Seniority seniority, List<String> extraResponsibilities)`
  - `Team(List<TeamMember> members)` — helpers: `byRole(BaseRole)`, `leads()`
  - `ArchitectureDoc(String stack, List<String> services, List<String> constraints, String deploymentModel, String rationale)`
- Enums — do not modify:
  - `BaseRole`: FRONTEND_DEV, BACKEND_DEV, FULLSTACK_DEV, QA_ENGINEER, DEVOPS, MOBILE_DEV
  - `Seniority`: JUNIOR, MID, SENIOR, LEAD, PRINCIPAL
- `PipelineException.java` — constructors:
  - `PipelineException(String message, String phase, int loopCount)`
  - `PipelineException(String message, String phase, int loopCount, Throwable cause)`
- `ScrumMasterOrchestrator.java` — YOUR TARGET FILE.
  Inject `LlmClient` via constructor.

## Project status
- mvn clean compile → SUCCESS
- mvn test → 63/63 passing

---

## Your task

Implement `ScrumMasterOrchestrator.java` — one method: `estimateTasks()`

---

## System prompt — define as private static final String constant

```java
private static final String SCRUM_MASTER_SYSTEM_PROMPT = """
    You are a Scrum Master who knows the full technical stack and the team.
    You will simulate a Planning Poker session for all tasks provided.

    For each task:
    - Estimate story points using Fibonacci scale only: 1, 2, 3, 5, 8, 13, 21
    - Simulate a vote from each team member (use their name as key, Fibonacci value as value)
    - storyPoints is the consensus (median or most common vote)
    - Only assign dependencies that are real technical blockers

    Rules:
    - Justify estimates >= 8 with a concrete technical risk in the task description
    - votes must include every team member by name
    - adjacency maps each taskId to the list of taskIds that must be completed before it
    - A task with no dependencies maps to an empty array in adjacency
    - pointsByRole maps each BaseRole name to total points of tasks primarily owned by that role
    - All storyPoints values must be from the Fibonacci scale: 1, 2, 3, 5, 8, 13, 21

    Respond ONLY in JSON with this exact format, no preamble, no markdown:
    {
      "tasks": [
        {
          "taskId": "task-id",
          "storyPoints": 5,
          "votes": {"Alice": 5, "Bob": 3}
        }
      ],
      "adjacency": {
        "task-001": ["task-002"],
        "task-002": []
      },
      "totalPoints": 42,
      "pointsByRole": {
        "BACKEND_DEV": 21,
        "FRONTEND_DEV": 13,
        "QA_ENGINEER": 8
      }
    }
    """;
```

> Note: `dependencies` was removed from the `tasks[]` array in the JSON format. `adjacency` is the single source of truth for task dependencies.

---

## Method: estimateTasks()

**Signature:**
```java
public TaskGraph estimateTasks(
    List<Task> tasks,
    ArchitectureDoc architecture,
    Team team
)
```

**Purpose:** Send all tasks, the architecture and the team to the LLM Scrum Master,
parse the JSON response and build a fully populated `TaskGraph`.

**Exact userMessage format:**

```
=== ARCHITECTURE ===
Stack: {architecture.stack()}
Deployment: {architecture.deploymentModel()}
Services: {service1}, {service2}, ...
Constraints: {constraint1}, {constraint2}, ...
Rationale: {architecture.rationale()}

=== TEAM ===
- {member.name()} | {member.baseRole()} | {member.seniority()}
...

=== TASKS ===
[{task.id()}] {task.title()}
Epic: {task.epicId() or "(none)"} | Story: {task.userStory() or "(none)"}
{task.description() or "(none)"}

[{task.id()}] {task.title()}
...
```

Each task is separated by a blank line. Services and Constraints are comma-separated on a single line. If a list is null or empty, write `(none)`. If `epicId`, `userStory` or `description` is null or blank, render `(none)` for that field.

**Concrete example** — given 2 tasks and 2 team members:

```
=== ARCHITECTURE ===
Stack: Java 21 + Spring Boot 3.3 + PostgreSQL
Deployment: Docker + AWS ECS
Services: API Gateway, Auth Service
Constraints: Must support 10k concurrent users
Rationale: Chosen for team familiarity and scalability.

=== TEAM ===
- Alice | BACKEND_DEV | SENIOR
- Bob | QA_ENGINEER | MID

=== TASKS ===
[task-001] Implement JWT authentication
Epic: epic-auth | Story: As a user I want to log in securely
Implement JWT token generation and validation using Spring Security.

[task-002] Write integration tests for auth flow
Epic: (none) | Story: (none)
(none)
```

**Implementation steps:**

1. Validate inputs:
   - If `tasks` is null or empty: throw `PipelineException("tasks is null or empty", "Phase4", 0)`
   - If `architecture` is null: throw `PipelineException("architecture is null", "Phase4", 0)`
   - If `team` is null or `team.members()` is null or empty: throw `PipelineException("team is null or empty", "Phase4", 0)`

2. Build `userMessage` using the exact format above. Use private helpers:
   - `formatList(List<String> items)` — returns comma-separated string, or `"(none)"` if null/empty
   - `formatTeam(Team team)` — returns each member as `"- name | baseRole | seniority\n"`
   - `formatTasks(List<Task> tasks)` — returns each task block separated by `"\n\n"`. For each task: if `epicId`, `userStory` or `description` is null or blank, render `"(none)"` for that field.

3. Call `llmClient.chat(SCRUM_MASTER_SYSTEM_PROMPT, userMessage)`

4. If response is null or blank:
   throw `PipelineException("Empty response from scrum master LLM", "Phase4", 0)`

5. Parse with `JsonParser.parse(response)` — wrap RuntimeException:
   throw `PipelineException("Failed to parse planning poker JSON", "Phase4", 0, e)`

6. Parse `adjacency` first → `Map<String, List<String>>`:
   - For each field in the `adjacency` object node: key = taskId string, value = list of taskId strings
   - Default `Collections.emptyMap()` if field missing

7. Parse `tasks` array from JSON — for each element build an `EstimatedTask`:
   - `taskId` → String: find the matching `Task` from the input list by `task.id().equals(taskId)` (case-sensitive). If not found, skip with `log.warn("Unknown taskId in LLM response: {}")`
   - `storyPoints` → int: read value, then validate with `toFibonacci(int value)` helper (see below). Default `1` if field missing.
   - `votes` → `Map<String, Integer>`: iterate the `votes` object node. Default `Collections.emptyMap()` if missing.
   - `dependencies` → `List<String>`: **do not read from the task JSON element**. Instead look up `adjacency.getOrDefault(taskId, Collections.emptyList())`.

8. Parse `totalPoints` → int (default `0` if missing)

9. Parse `pointsByRole` → `Map<String, Integer>`: iterate the object node. Default `Collections.emptyMap()` if missing.

10. Return `new TaskGraph(estimatedTasks, adjacency, totalPoints, pointsByRole)`

**Private helper: `toFibonacci(int value)`**

```
Valid Fibonacci values: {1, 2, 3, 5, 8, 13, 21}
- If value is already in the set: return as-is
- If value < 1: log.warn("Invalid storyPoints {}, defaulting to 1"); return 1
- If value > 21: log.warn("Invalid storyPoints {}, defaulting to 21"); return 21
- Otherwise: return the nearest Fibonacci value (round up to next Fibonacci)
  Mapping: 4→5, 6→8, 7→8, 9→13, 10→13, 11→13, 12→13, 14→21, ...20→21
  log.warn("Non-Fibonacci storyPoints {}, corrected to {}")
```

**Logging:**
- `log.info()` on entry: task count + team member count
- `log.info()` after parsing: totalPoints + estimated task count
- `log.warn()` for each unknown taskId in LLM response
- `log.warn()` for each storyPoints value corrected by `toFibonacci()`
- `log.error()` on any caught exception before rethrowing

---

## Coding standards
- Constructor injection only
- @Service + @Slf4j
- No @Autowired on fields
- All JSON parsing via `JsonParser.parse()`
- System prompt as private static final String constant
- Missing JSON fields use safe defaults — never null, never exception
- `formatList()`, `formatTeam()`, `formatTasks()`, `toFibonacci()` are private helper methods
- Task matching by ID is case-sensitive
- `adjacency` is the single source of truth for dependencies — never read `dependencies` from the task JSON element

---

## Testing requirements (TDD)

Test file: `src/test/java/com/ideapipeline/orchestrator/ScrumMasterOrchestratorTest.java`

### Tests required

- `estimateTasks_shouldReturnFullyPopulatedTaskGraph`
  Mock valid JSON with 2 tasks, adjacency, totalPoints and pointsByRole.
  Verify `TaskGraph.tasks().size() == 2`, totalPoints, pointsByRole populated.

- `estimateTasks_shouldMapVotesCorrectly`
  Mock response with `"votes": {"Alice": 5, "Bob": 3}` for a task.
  Verify `EstimatedTask.votes()` == `{"Alice": 5, "Bob": 3}`.

- `estimateTasks_shouldPopulateDependenciesFromAdjacency`
  Mock adjacency: `{"task-001": ["task-002"], "task-002": []}`.
  Verify `EstimatedTask` for task-001 has `dependencies == ["task-002"]`.
  Verify `EstimatedTask` for task-002 has `dependencies == []`.
  Confirm no `dependencies` field is read from the `tasks[]` array elements.

- `estimateTasks_shouldSkipUnknownTaskIdWithWarn`
  Mock response with a taskId not present in input list.
  Verify the unknown task is not included in `TaskGraph.tasks()`.

- `estimateTasks_shouldUseDefaultsForMissingFields`
  Mock response with a task entry missing `storyPoints` and `votes`.
  Verify storyPoints == 1, votes is empty map.

- `estimateTasks_shouldCorrectNonFibonacciStoryPoints`
  Mock response with `"storyPoints": 4` for a task.
  Verify `EstimatedTask.storyPoints() == 5` (nearest Fibonacci, rounded up).

- `estimateTasks_shouldCorrectStoryPointsBelowOne`
  Mock response with `"storyPoints": 0`.
  Verify `EstimatedTask.storyPoints() == 1`.

- `estimateTasks_shouldCorrectStoryPointsAboveTwentyOne`
  Mock response with `"storyPoints": 34`.
  Verify `EstimatedTask.storyPoints() == 21`.

- `estimateTasks_shouldHandleMarkdownFencedResponse`
  Mock response with ` ```json ` fences.
  Verify TaskGraph is parsed correctly.

- `estimateTasks_shouldThrowPipelineException_onNullTasks`
  Verify PipelineException with phase `"Phase4"`.

- `estimateTasks_shouldThrowPipelineException_onEmptyTasks`
  Verify PipelineException with phase `"Phase4"`.

- `estimateTasks_shouldThrowPipelineException_onNullArchitecture`
  Verify PipelineException with phase `"Phase4"`.

- `estimateTasks_shouldThrowPipelineException_onNullTeam`
  Verify PipelineException with phase `"Phase4"`.

- `estimateTasks_shouldThrowPipelineException_onEmptyTeamMembers`
  Verify PipelineException with phase `"Phase4"`.

- `estimateTasks_shouldThrowPipelineException_onEmptyLlmResponse`
  Verify PipelineException with phase `"Phase4"`.

- `estimateTasks_shouldThrowPipelineException_onInvalidJson`
  Verify PipelineException with phase `"Phase4"`.

- `estimateTasks_shouldFormatArchitectureInUserMessage`
  Use ArgumentCaptor.
  Verify userMessage contains `"=== ARCHITECTURE ==="`, `"Stack:"`, `"Deployment:"`.

- `estimateTasks_shouldFormatTeamMembersInUserMessage`
  Pass team with 2 known members.
  Use ArgumentCaptor.
  Verify userMessage contains `"=== TEAM ==="` and each member as `"- name | ROLE | SENIORITY"`.

- `estimateTasks_shouldFormatTasksInUserMessage`
  Pass 2 tasks with known ids and titles.
  Use ArgumentCaptor.
  Verify userMessage contains `"=== TASKS ==="` and each task formatted as `"[task-id] title"`.

- `estimateTasks_shouldRenderNoneForNullOrEmptyArchitectureLists`
  Pass `ArchitectureDoc` with null services and empty constraints.
  Use ArgumentCaptor.
  Verify userMessage contains `"(none)"` for both.

- `estimateTasks_shouldRenderNoneForBlankTaskFields`
  Pass a `Task` with null `epicId`, blank `userStory` and null `description`.
  Use ArgumentCaptor.
  Verify userMessage contains `"Epic: (none) | Story: (none)"` and `"(none)"` for description.

---

## Acceptance criteria
1. `mvn clean compile` → SUCCESS
2. `mvn test` → 21 new tests pass (ScrumMasterOrchestratorTest)
3. `mvn test` → 84 total tests passing
4. `estimateTasks()` has no `UnsupportedOperationException`
5. Unknown task IDs in LLM response are skipped with `log.warn()`, not exception
6. All map and list fields default to empty collections, never null
7. Task matching by ID is case-sensitive
8. `storyPoints` defaults to `1` when missing; corrected to nearest Fibonacci when invalid
9. `adjacency` is the single source of truth for `EstimatedTask.dependencies()`
10. All JSON parsing via `JsonParser.parse()`
11. System prompt defined as private static final constant

---
