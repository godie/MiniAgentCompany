# idea-pipeline.prompt.md v1.0

1. Validación de team.members() vacío — añadir en el bloque de validación inicial:
javaif (team == null || team.members() == null || team.members().isEmpty()) {
    throw new PipelineException("team is null or empty", "Phase4", 0);
}
2. Nombre de getDebateRounds() — verificar en PipelineProperties.java si el método se llama getDebateRounds() o getDefaultDebateRounds() y usar el nombre real. Si no existe ninguno, crearlo.

## Role
You are a Senior Java Developer implementing `IdeaPipeline.java` — the main orchestrator of the idea-pipeline system. This class is **not an LLM agent**: it is pure deterministic Java code that coordinates all phases in order, manages the convergence loop, and produces the final `PipelineResult`.

## Context
Read these files before writing any code:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

---

## What already exists — do not modify

- All orchestrators — fully implemented and tested:
  - `GatekeeperOrchestrator` — methods: `generateQuestions(RawIdea)`, `enrichContext(RawIdea, List<RefinementQA>)`
  - `DebateOrchestrator` — method: `runDebate(IdeaContext, int rounds)`
  - `SynthesisOrchestrator` — method: `synthesize(IdeaContext, List<DebateMessage>)`
  - `CriticOrchestrator` — method: `critique(PipelineOutput, List<DebateMessage>)`
  - `ValidationOrchestrator` — method: `validate(PipelineOutput, CritiqueResult, int loopCount)`
  - `StackArchitectOrchestrator` — method: `defineStack(IdeaContext, PipelineOutput, List<DebateMessage>)`
  - `ScrumMasterOrchestrator` — method: `estimateTasks(List<Task>, ArchitectureDoc, Team)`
- `PipelineProperties.java` — inject to read:
  - `getConvergenceThreshold()` → int (default 75)
  - `getMaxLoops()` → int (default 3)
  - `getDebateRounds()` → int (default 2)
- `TaskParser.java` — already exists:
  - `public static List<Task> parse(String taskDocument)` — parses `tasks.md` markdown into `List<Task>`
- All records — do not modify:
  - `RawIdea(String description)`
  - `RefinementQA(String question, String answer)`
  - `IdeaContext(String rawIdea, List<RefinementQA> refinements, String enrichedSummary)`
  - `DebateMessage(String agentName, String content, int round)`
  - `PipelineOutput(String contextDocument, String flowDocument, String taskDocument)`
  - `CritiqueResult(List<String> critiques, List<String> gaps, List<String> refinementNeeds)`
  - `ValidationResult(int convergenceScore, boolean shouldLoop, String reasoning)`
  - `ArchitectureDoc(String stack, List<String> services, List<String> constraints, String deploymentModel, String rationale)`
  - `Task(String id, String title, String description, String epicId, String userStory)`
  - `TaskGraph(List<EstimatedTask> tasks, Map<String, List<String>> adjacency, int totalPoints, Map<String, Integer> pointsByRole)`
  - `PipelineState(IdeaContext context, List<DebateMessage> debateHistory, PipelineOutput output, int loopCount)`
  - `PipelineResult(IdeaContext context, PipelineOutput documents, ArchitectureDoc architecture, TaskGraph taskGraph, int loopsRequired)`
- `PipelineException.java` — constructors:
  - `PipelineException(String message, String phase, int loopCount)`
  - `PipelineException(String message, String phase, int loopCount, Throwable cause)`
- `IdeaPipeline.java` — YOUR TARGET FILE.
  Inject all orchestrators and `PipelineProperties` via constructor.

## Project status
- mvn clean compile → SUCCESS
- mvn test → 85/85 passing

---

## Your task

Implement `IdeaPipeline.java` — one public method: `run()`

---

## Method: run()

**Signature:**
```java
public PipelineResult run(
    RawIdea rawIdea,
    List<RefinementQA> refinements,
    Team team
)
```

**Purpose:** Execute all pipeline phases in order, manage the convergence loop between phases 1–3b, and return a fully populated `PipelineResult`.

---

## Full execution flow

### Phase 0 — Context building (sequential)
1. Call `gatekeeperOrchestrator.enrichContext(rawIdea, refinements)` → `IdeaContext`

### Phase 1 — Debate (sequential)
2. Call `debateOrchestrator.runDebate(ideaContext, debateRounds)` → `List<DebateMessage>`
   - `debateRounds` comes from `pipelineProperties.getDebateRounds()`

### Phase 2 — Document Synthesis (sequential)
3. Call `synthesisOrchestrator.synthesize(ideaContext, debateHistory)` → `PipelineOutput`

### Phases 3a + 3b — Critique + Validation loop
4. Enter the convergence loop. Repeat while `loopCount < maxLoops`:

   **a.** Call `criticOrchestrator.critique(pipelineOutput, debateHistory)` → `CritiqueResult`

   **b.** Call `validationOrchestrator.validate(pipelineOutput, critiqueResult, loopCount)` → `ValidationResult`

   **c.** Log `convergenceScore` and `shouldLoop`

   **d.** If `convergenceScore >= convergenceThreshold` OR `!shouldLoop`:
   - Break the loop — documents have converged

   **e.** If `loopCount + 1 >= maxLoops`:
   - Log warning: `"Forcing convergence after {} loops"` with loopCount + 1
   - Break the loop — max loops reached

   **f.** Otherwise (loop continues):
   - Enrich `IdeaContext`: build a new `IdeaContext` appending gaps and refinementNeeds from `CritiqueResult` to the `enrichedSummary`:
     ```
     {original enrichedSummary}

     --- Refinement round {loopCount + 1} ---
     Gaps: {gap1}; {gap2}; ...
     Needs: {need1}; {need2}; ...
     ```
   - Re-run Phase 1 with the enriched `IdeaContext` → new `debateHistory`
   - Re-run Phase 2 with enriched `IdeaContext` + new `debateHistory` → new `PipelineOutput`
   - Increment `loopCount`

### Phase 3.5 — Stack Architecture (sequential, after loop exits)
5. Call `stackArchitectOrchestrator.defineStack(ideaContext, pipelineOutput, debateHistory)` → `ArchitectureDoc`

### Phase 4 — Planning Poker (sequential)
6. Call `TaskParser.parse(pipelineOutput.taskDocument())` → `List<Task>`
7. Call `scrumMasterOrchestrator.estimateTasks(tasks, architectureDoc, team)` → `TaskGraph`

### Return
8. Return `new PipelineResult(ideaContext, pipelineOutput, architectureDoc, taskGraph, loopCount)`

---

## Validation

- If `rawIdea` is null or `rawIdea.description()` is blank:
  throw `PipelineException("rawIdea is null or empty", "Phase0", 0)`
- If `refinements` is null: treat as `Collections.emptyList()` — no exception
- If `team` is null:
  throw `PipelineException("team is null", "Phase4", 0)`
- All other exceptions from orchestrators propagate as-is — do not wrap them

---

## Structured concurrency — NOT used in this implementation

The current implementation is **sequential only**. Do NOT use `StructuredTaskScope`, `Thread.ofVirtual()`, or `CompletableFuture`. Phases 1 and 2 run sequentially within the loop. Parallel execution is a future enhancement — do not implement it now.

---

## Logging

- `log.info()` on entry: rawIdea description length + team member count
- `log.info()` at start of each phase: phase name + loopCount where applicable
- `log.info()` after Phase 3b each iteration: `convergenceScore`, `shouldLoop`, `loopCount`
- `log.warn()` when forcing convergence after maxLoops
- `log.info()` on successful completion: final loopCount + totalPoints from TaskGraph
- `log.error()` on any caught exception before rethrowing

---

## Coding standards
- Constructor injection only — inject all 7 orchestrators + `PipelineProperties`
- @Service + @Slf4j
- No @Autowired on fields
- Null `refinements` treated as empty list — never throw
- All exceptions from orchestrators propagate unchanged
- `loopCount` starts at `0` and represents completed loops — it is passed directly to `validationOrchestrator.validate()`
- The `ideaContext` variable is reassigned inside the loop when enriching — use a local variable, not a field

---

## Testing requirements (TDD)

Test file: `src/test/java/com/ideapipeline/pipeline/IdeaPipelineTest.java`

### Setup
Mock all 7 orchestrators and `PipelineProperties`. Configure `PipelineProperties` defaults:
- `getConvergenceThreshold()` → 75
- `getMaxLoops()` → 3
- `getDebateRounds()` → 2

Use a static helper `mockValidationResult(int score, boolean shouldLoop)` to build `ValidationResult` mocks cleanly across tests.

### Tests required

- `run_shouldExecuteFullPipelineAndReturnResult`
  Mock all orchestrators with valid returns. Mock `ValidationResult` with score 85, shouldLoop false.
  Verify `PipelineResult` is not null, `loopsRequired == 0`, `taskGraph` is not null.

- `run_shouldCallPhasesInOrder`
  Use `InOrder` Mockito verifier.
  Verify call order: `enrichContext` → `runDebate` → `synthesize` → `critique` → `validate` → `defineStack` → `estimateTasks`.

- `run_shouldLoopOnceThenConverge`
  First `validate()` call returns score 50, shouldLoop true.
  Second `validate()` call returns score 85, shouldLoop false.
  Verify `runDebate` called twice, `synthesize` called twice, `loopsRequired == 1`.

- `run_shouldForceConvergenceAfterMaxLoops`
  All `validate()` calls return score 40, shouldLoop true. maxLoops = 3.
  Verify `runDebate` called 3 times total (1 initial + 2 loops — loop runs maxLoops times but breaks before re-running again after the last one).
  Verify `defineStack` is still called after forced convergence.
  Verify `loopsRequired == 2`.

- `run_shouldEnrichIdeaContextWithGapsOnLoop`
  First `validate()` returns score 50, shouldLoop true, with a `CritiqueResult` containing gaps `["gap1"]` and refinementNeeds `["need1"]`.
  Second `validate()` returns score 85, shouldLoop false.
  Use ArgumentCaptor on `runDebate` — capture the `IdeaContext` passed on the second call.
  Verify the second `IdeaContext.enrichedSummary()` contains `"gap1"` and `"need1"`.

- `run_shouldPassLoopCountToValidate`
  First `validate()` returns score 50, shouldLoop true.
  Second `validate()` returns score 85, shouldLoop false.
  Use ArgumentCaptor on `validate` — verify first call has loopCount 0, second call has loopCount 1.

- `run_shouldThrowPipelineException_onNullRawIdea`
  Verify PipelineException with phase `"Phase0"`.

- `run_shouldThrowPipelineException_onBlankRawIdeaDescription`
  Pass `RawIdea` with blank description.
  Verify PipelineException with phase `"Phase0"`.

- `run_shouldThrowPipelineException_onNullTeam`
  Verify PipelineException with phase `"Phase4"`.

- `run_shouldTreatNullRefinementsAsEmptyList`
  Pass `null` refinements.
  Verify no exception is thrown and `enrichContext` is called successfully.

- `run_shouldPropagateExceptionFromOrchestrator`
  Mock `synthesisOrchestrator.synthesize()` to throw `PipelineException("synthesis failed", "Phase2", 0)`.
  Verify the same exception propagates out of `run()` unchanged.

---

## Acceptance criteria
1. `mvn clean compile` → SUCCESS
2. `mvn test` → 11 new tests pass (IdeaPipelineTest)
3. `mvn test` → 96 total tests passing
4. `run()` executes all 7 phases in the correct order
5. Loop re-runs phases 1 and 2 with enriched context on each iteration
6. `loopCount` in returned `PipelineResult` reflects completed loops (0 if converged on first attempt)
7. Forced convergence after `maxLoops` still proceeds to phases 3.5 and 4
8. Null `refinements` never throws — treated as empty list
9. Exceptions from orchestrators propagate unchanged — no re-wrapping
10. No structured concurrency — sequential implementation only
