# phase-4-planning-poker.prompt.md v1.0

## Role
You are a Senior Java Developer implementing Phase 4 of the idea-pipeline system.
Phase 4 is the final phase. It transforms the task document (tasks.md) into a
fully estimated TaskGraph by parsing tasks from Markdown and running a
simulated Planning Poker session with the LLM.

## Context
Read these files before writing any code:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

---

## What already exists — do not modify

These files are FULLY IMPLEMENTED and tested:

- `LlmClient.java` — interface:
  - `chat(String systemPrompt, String userMessage)`
  - `chatWithHistory(String systemPrompt, List<DebateMessage> history, String userMessage)`

- `LlmClientImpl.java` — @Component, fully implemented. 6 tests passing.

- `JsonParser.java` — package `com.ideapipeline.client`
  `public static JsonNode parse(String response)`

- `GatekeeperOrchestrator.java` — fully implemented. 8 tests passing.
- `DebateOrchestrator.java` — fully implemented. 9 tests passing.
- `SynthesisOrchestrator.java` — fully implemented. 8 tests passing.
- `CriticOrchestrator.java` — fully implemented. 9 tests passing.
- `ValidationOrchestrator.java` — fully implemented. 12 tests passing.
- `StackArchitectOrchestrator.java` — fully implemented. 11 tests passing.

- `Task.java` — record, do not modify:
  `Task(String id, String title, String description, String epicId, String userStory)`
- `EstimatedTask.java` — record, do not modify:
  `EstimatedTask(Task task, int storyPoints, Map<String, Integer> votes, List<String> dependencies)`
- `TaskGraph.java` — record, do not modify:
  `TaskGraph(List<EstimatedTask> tasks, Map<String, List<String>> adjacency, int totalPoints, Map<String, Integer> pointsByRole)`
- `Team.java` — record, do not modify.
- `TeamMember.java` — record, do not modify.
- `ArchitectureDoc.java` — record, do not modify.

- `PipelineException.java` — constructors available.

- `PlanningPokerOrchestrator.java` — YOUR TARGET FILE
  Currently returns hardcoded tasks with fake estimates.
  Inject `LlmClient` via constructor:
  `public PlanningPokerOrchestrator(LlmClient llmClient)`

---

## Project status
- `mvn clean compile` → SUCCESS
- `mvn test` → 62/62 passing

---

## Important: tasks.md format (ADR-002)

Before implementing, read `docs/decisions/ADR-002-tasks-markdown-format.md`.
The tasks.md now follows a hardened format with:
- Sequential IDs: EPIC-1, EPIC-2 / US-1, US-2 / TASK-1, TASK-2
- `<!-- META: {"..."} -->` comment blocks with JSON metadata (id, epicId, userStory, dependencies)
- Machine-parseable and human-readable

---

## Your task

Implement `PlanningPokerOrchestrator.java` — one method: `runPlanningPoker()`,
plus a private helper: `parseTasksFromMarkdown()`.

This phase has TWO responsibilities:
1. **4A — Parse** tasks.md Markdown into `List<Task>` objects
2. **4B — Estimate** tasks via LLM simulated Planning Poker → `TaskGraph`

---

## Phase 4A: Markdown Parser

### System prompt constant

```java
private static final String TASK_PARSE_SYSTEM_PROMPT = """
You are a precise parser. Given a tasks.md document in Markdown,
extract ALL tasks, epics, and user stories.

The Markdown follows this structure:
## Epic N: [Epic Name]
### User Story: [Story Title]
#### Task: [Task Title]
- Description: [description text]
- Acceptance criteria: [criteria text]
- Estimate: S | M | L

Extract every task and return ONLY JSON with this exact format:
{
  "tasks": [
    {
      "id": "TASK-1",
      "title": "task title",
      "description": "description text",
      "epicId": "EPIC-1",
      "userStory": "US-1",
      "sizeEstimate": "S"
    }
  ],
  "epics": [
    {"id": "EPIC-1", "name": "Epic Name"}
  ],
  "userStories": [
    {"id": "US-1", "title": "Story Title", "epicId": "EPIC-1"}
  ]
}

IDs must be sequential: EPIC-1, EPIC-2, ... / US-1, US-2, ... / TASK-1, TASK-2, ...
The size estimate is the original S|M|L from the document.
""";
```

### Implementation: `parseTasksFromMarkdown()`

```java
private List<Task> parseTasksFromMarkdown(String tasksMarkdown) {
    if (tasksMarkdown == null || tasksMarkdown.isBlank()) {
        log.warn("Empty tasks markdown, returning empty task list");
        return List.of();
    }
    
    try {
        String response = llmClient.chat(TASK_PARSE_SYSTEM_PROMPT, tasksMarkdown);
        
        if (response == null || response.isBlank()) {
            throw new PipelineException("Empty response from task parser LLM", "Phase4", 0);
        }
        
        JsonNode node = JsonParser.parse(response);
        JsonNode tasksArray = node.get("tasks");
        
        if (tasksArray == null || !tasksArray.isArray()) {
            log.warn("No tasks array in parser response, returning empty list");
            return List.of();
        }
        
        List<Task> tasks = new ArrayList<>();
        for (JsonNode taskNode : tasksArray) {
            tasks.add(new Task(
                taskNode.get("id").asText(),
                taskNode.get("title").asText(),
                taskNode.get("description").asText(),
                taskNode.get("epicId").asText(),
                taskNode.get("userStory").asText()
            ));
        }
        
        log.info("Parsed {} tasks from markdown", tasks.size());
        return tasks;
        
    } catch (PipelineException e) {
        throw e;
    } catch (RuntimeException e) {
        throw new PipelineException("Failed to parse tasks from markdown", "Phase4", 0, e);
    }
}
```

---

## Phase 4B: Planning Poker via LLM

### System prompt constant

```java
private static final String PLANNING_POKER_SYSTEM_PROMPT = """
You are a Scrum Master facilitating a Planning Poker session with a
development team. You know the architecture and the team composition.

Your job: estimate story points for each task using Planning Poker.
Use Fibonacci sequence: 1, 2, 3, 5, 8, 13, 21.

For EACH task, provide estimates from the perspective of EVERY team member
who would be involved. Also identify task dependencies.

The team is provided in the user message with their roles and seniority.

The architecture (stack, services, constraints) is also provided.

Rules:
- Story points represent COMPLEXITY + EFFORT + UNCERTAINTY
- Dependencies: a task depends on another if it cannot START before
  the other is DONE
- Higher seniority typically estimates LOWER (more experience)
- QA members estimate higher than devs (they account for testing)
- DevOps members estimate for deployment/infrastructure tasks

Respond ONLY in JSON with this exact format:
{
  "tasks": [
    {
      "taskId": "TASK-1",
      "storyPoints": 5,
      "votes": {
        "memberId1": 5,
        "memberId2": 8,
        "memberId3": 3
      },
      "dependencies": ["TASK-0"]
    }
  ],
  "adjacency": {
    "TASK-0": ["TASK-1", "TASK-2"],
    "TASK-1": ["TASK-3"]
  },
  "totalPoints": 34,
  "pointsByRole": {
    "BACKEND_DEV": 21,
    "FRONTEND_DEV": 13
  }
}

- storyPoints: the FINAL consensus estimate (median of votes, rounded up
  to nearest Fibonacci number)
- votes: map of team member ID → their individual estimate
- dependencies: list of task IDs this task depends on (empty list if none)
- adjacency: dependency graph (parent task → list of child tasks)
- totalPoints: sum of all story points
- pointsByRole: sum of story points grouped by BaseRole enum value

IMPORTANT:
- Every task in the input MUST appear in the output
- Every team member MUST have a vote for each task they'd be involved in
- totalPoints MUST be the sum of all storyPoints values
- pointsByRole sums MUST be consistent with totalPoints
""";
```

---

## Implementation: `runPlanningPoker()`

**Signature:**
```java
public TaskGraph runPlanningPoker(
    String tasksMarkdown,
    Team team,
    ArchitectureDoc architecture
) throws Exception
```

**Purpose:** Parse tasks from Markdown, then estimate them via simulated
Planning Poker with the given team and architecture.

---

### Implementation steps

1. **Validate inputs**
   - If `tasksMarkdown == null || tasksMarkdown.isBlank()`:
     throw `PipelineException("tasksMarkdown is null or blank", "Phase4", 0)`
   - If `team == null || team.members() == null || team.members().isEmpty()`:
     throw `PipelineException("team is null or empty", "Phase4", 0)`
   - If `architecture == null`:
     throw `PipelineException("architecture is null", "Phase4", 0)`

2. **Parse tasks from Markdown** (Phase 4A)
   ```java
   List<Task> parsedTasks = parseTasksFromMarkdown(tasksMarkdown);
   
   if (parsedTasks.isEmpty()) {
       log.warn("No tasks parsed from markdown, returning empty TaskGraph");
       return new TaskGraph(List.of(), Map.of(), 0, Map.of());
   }
   ```

3. **Build user message for Planning Poker**

   ```
   === TEAM ===
   [team formatted as role+seniority list]

   === ARCHITECTURE ===
   Stack: [architecture.stack()]
   Services: [architecture.services() joined by ", "]
   Deployment: [architecture.deploymentModel()]
   Constraints: [architecture.constraints() joined by ", "]

   === TASKS TO ESTIMATE ===
   [list of tasks formatted as "id: title — description"]
   ```

   Team formatting:
   ```
   - [member.id()]: [member.name()] — [member.baseRole()] ([member.seniority()])
     Extra responsibilities: [member.extraResponsibilities() joined by ", "]
   ```

4. **Call LLM**
   ```java
   String response = llmClient.chat(PLANNING_POKER_SYSTEM_PROMPT, userMessage);
   ```

5. **Validate response**
   - If `response == null || response.isBlank()`:
     throw `PipelineException("Empty response from planning poker LLM", "Phase4", 0)`

6. **Parse JSON to `TaskGraph`**
   ```java
   try {
       JsonNode node = JsonParser.parse(response);
       
       // Parse tasks
       List<EstimatedTask> estimatedTasks = new ArrayList<>();
       JsonNode tasksArray = node.get("tasks");
       for (JsonNode taskNode : tasksArray) {
           String taskId = taskNode.get("taskId").asText();
           int storyPoints = taskNode.get("storyPoints").asInt();
           
           // Find original Task object
           Task originalTask = parsedTasks.stream()
               .filter(t -> t.id().equals(taskId))
               .findFirst()
               .orElseThrow(() -> new PipelineException(
                   "Unknown task ID in LLM response: " + taskId, "Phase4", 0));
           
           // Parse votes
           Map<String, Integer> votes = new HashMap<>();
           JsonNode votesNode = taskNode.get("votes");
           if (votesNode != null) {
               var fields = votesNode.fields();
               while (fields.hasNext()) {
                   var field = fields.next();
                   votes.put(field.getKey(), field.getValue().asInt());
               }
           }
           
           // Parse dependencies
           List<String> deps = new ArrayList<>();
           JsonNode depsNode = taskNode.get("dependencies");
           if (depsNode != null && depsNode.isArray()) {
               for (JsonNode dep : depsNode) {
                   deps.add(dep.asText());
               }
           }
           
           estimatedTasks.add(new EstimatedTask(originalTask, storyPoints, votes, deps));
       }
       
       // Parse adjacency
       Map<String, List<String>> adjacency = new HashMap<>();
       JsonNode adjNode = node.get("adjacency");
       if (adjNode != null) {
           var fields = adjNode.fields();
           while (fields.hasNext()) {
               var field = fields.next();
               List<String> children = new ArrayList<>();
               for (JsonNode child : field.getValue()) {
                   children.add(child.asText());
               }
               adjacency.put(field.getKey(), children);
           }
       }
       
       int totalPoints = node.get("totalPoints").asInt();
       
       // Parse pointsByRole
       Map<String, Integer> pointsByRole = new HashMap<>();
       JsonNode pbrNode = node.get("pointsByRole");
       if (pbrNode != null) {
           var fields = pbrNode.fields();
           while (fields.hasNext()) {
               var field = fields.next();
               pointsByRole.put(field.getKey(), field.getValue().asInt());
           }
       }
       
       // Validate consistency
       int computedTotal = estimatedTasks.stream()
           .mapToInt(EstimatedTask::storyPoints)
           .sum();
       if (computedTotal != totalPoints) {
           log.warn("totalPoints mismatch: declared={}, computed={}", totalPoints, computedTotal);
       }
       
       return new TaskGraph(estimatedTasks, adjacency, totalPoints, pointsByRole);
       
   } catch (PipelineException e) {
       throw e;
   } catch (RuntimeException e) {
       throw new PipelineException("Failed to parse planning poker JSON", "Phase4", 0, e);
   }
   ```

---

## Logging

- `log.info()` on method entry: markdown length, team size, architecture summary
- `log.info()` after parsing: number of tasks extracted
- `log.info()` after planning poker: totalPoints, task count
- `log.warn()` if no tasks parsed from markdown
- `log.warn()` if `totalPoints` in response doesn't match computed sum
- `log.error()` on any caught exception before rethrowing

---

## Exception handling

```java
try {
    // ... parse + estimate ...
} catch (PipelineException e) {
    throw e;
} catch (RuntimeException e) {
    throw new PipelineException("Planning poker failed", "Phase4", 0, e);
}
```

---

## Coding standards

- `@Service` + `@Slf4j` on class
- Constructor injection only — `public PlanningPokerOrchestrator(LlmClient llmClient)`
- No `@Autowired` on fields
- System prompts as `private static final String` constants
- All JSON parsing via `JsonParser.parse()`
- All LLM exceptions wrapped in `PipelineException`
- No hardcoded tasks or estimates — all output from LLM
- Remove all placeholder logic (`Arrays.asList(...)`, hardcoded `Task` objects, fake votes)
- `parseTasksFromMarkdown()` is a private method (separation of concerns)
- Cross-validate `totalPoints` from LLM against computed sum

---

## Testing requirements (TDD)

Test file: `src/test/java/com/ideapipeline/orchestrator/PlanningPokerOrchestratorTest.java`
Use `@ExtendWith(MockitoExtension.class)` and mock `LlmClient`.

---

### Parser tests (Phase 4A)

1. **`parseTasks_shouldExtractAllTasks`**
   - Provide markdown with 3 tasks, mock `llmClient.chat()` to return valid JSON
   - Call `runPlanningPoker()` and verify `taskGraph.tasks().size() == 3`

2. **`parseTasks_shouldPreserveEpicAndUserStoryIds`**
   - Verify `Task.epicId()` and `Task.userStory()` fields are populated
     from parsed JSON

3. **`parseTasks_shouldHandleEmptyMarkdown`**
   - Pass `""` as tasksMarkdown
   - Verify `TaskGraph` is returned with 0 tasks and 0 totalPoints

4. **`parseTasks_shouldThrowPipelineException_onInvalidParseJson`**
   - Mock parser LLM to return invalid JSON
   - Verify `PipelineException` is thrown with phase `"Phase4"`

---

### Orchestrator tests (Phase 4B)

5. **`runPlanningPoker_shouldReturnParsedTaskGraph`**
   - Mock parser LLM to return 2 tasks
   - Mock planning poker LLM to return valid TaskGraph JSON
   - Verify all fields are populated

6. **`runPlanningPoker_shouldThrowPipelineException_onInvalidJson`**
   - Mock parser LLM to return valid tasks
   - Mock planning poker LLM to return invalid JSON
   - Verify `PipelineException` is thrown

7. **`runPlanningPoker_shouldThrowPipelineException_onEmptyResponse`**
   - Mock `llmClient.chat()` to return `""` for planning poker call
   - Verify `PipelineException` is thrown

8. **`runPlanningPoker_shouldUseRealTeamMembers`**
   - Use `ArgumentCaptor<String>` to capture userMessage
   - Verify it contains team member names, roles, and seniority

9. **`runPlanningPoker_shouldUseArchitectureDoc`**
   - Verify userMessage contains architecture stack, services, constraints,
     and deploymentModel

10. **`runPlanningPoker_shouldThrowPipelineException_onEmptyTeam`**
    - Pass `Team` with empty members list
    - Verify `PipelineException` is thrown

11. **`runPlanningPoker_shouldThrowPipelineException_onNullTasksMarkdown`**
    - Pass `null` as tasksMarkdown
    - Verify `PipelineException` is thrown

12. **`runPlanningPoker_shouldThrowPipelineException_onUnknownTaskId`**
    - Mock parser to return tasks `["TASK-1"]`
    - Mock poker to return task with id `"TASK-999"`
    - Verify `PipelineException` is thrown

13. **`runPlanningPoker_shouldHandleMarkdownFencedResponse`**
    - Mock planning poker LLM to return JSON wrapped in ```json fences
    - Verify `JsonParser` strips fences and result is correctly parsed

14. **`runPlanningPoker_shouldLogWarningOnTotalPointsMismatch`**
    - Mock poker to return `totalPoints: 100` but sum of storyPoints is 50
    - Verify result still returned (with warning logged)

---

## Acceptance criteria

1. `mvn clean compile` → SUCCESS
2. `mvn test` → 14 new tests pass (PlanningPokerOrchestratorTest)
3. Total tests ≥ 75 passing
4. `runPlanningPoker()` has NO hardcoded tasks or estimates
5. Tasks are parsed from Markdown via LLM (Phase 4A)
6. Estimates are generated via LLM Planning Poker (Phase 4B)
7. Every task ID in the LLM response must match a parsed task
8. Team member names, roles, and seniority appear in the LLM prompt
9. Architecture details appear in the LLM prompt
10. `totalPoints` cross-validated with computed sum (warning if mismatch)
11. Null/empty inputs throw `PipelineException`
12. `JsonParser` RuntimeException is wrapped in `PipelineException`
13. System prompts defined as `private static final String` constants

---

## Files to modify

1. **`src/main/java/com/ideapipeline/orchestrator/PlanningPokerOrchestrator.java`**
   - Inject `LlmClient` via constructor
   - Replace hardcoded placeholder with:
     - `parseTasksFromMarkdown()` private method (Phase 4A)
     - LLM-based Planning Poker estimation (Phase 4B)
   - Add cross-validation of `totalPoints`

2. **`src/test/java/com/ideapipeline/orchestrator/PlanningPokerOrchestratorTest.java`**
   - Create new test file (or replace existing if one exists)
   - 14 test methods as specified above

---

## Success criteria

After implementation:
- ✅ Code compiles without warnings
- ✅ All 14 tests pass
- ✅ No hardcoded tasks or estimates remain
- ✅ Tasks are parsed from actual Markdown content
- ✅ Estimates include per-member votes and role breakdown
- ✅ Proper exception handling throughout
- ✅ Logging at appropriate levels

---

## Notes

- The `parseTasksFromMarkdown()` helper exists because `tasks.md` is
  free-form Markdown from the Synthesis phase. Parsing it programmatically
  without an LLM would be fragile given the variety of Markdown structures
  the synthesis LLM might produce.
- The `adjacency` map in `TaskGraph` represents a DAG (directed acyclic graph).
  The LLM should not produce cycles.
- `pointsByRole` uses `BaseRole` enum values (e.g., `BACKEND_DEV`, `FRONTEND_DEV`,
  `QA_ENGINEER`, `DEVOPS`, `FULLSTACK_DEV`, `MOBILE_DEV`).

---

End of phase-4-planning-poker.prompt.md v1.0
