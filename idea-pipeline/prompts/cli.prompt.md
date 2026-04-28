# cli.prompt.md v1.0

--idea blank: después de leer el flag, validar ideaText.isBlank() → exitWithError("Error: --idea cannot be empty")
Directorio padre de --output: antes de escribir, llamar Files.createDirectories(Path.of(outputPath).getParent()) — ignorar si ya existe, exitWithError si falla
team.members() vacío: en parseTeam(), después de deserializar, si team.members().isEmpty() → exitWithError("Error: team must have at least one member")
Respuesta vacía en modo interactivo: aceptar vacío pero imprimir "Warning: empty answer for question {i}" a stderr — no bloquear

## Role
You are a Senior Java Developer implementing the CLI entry point of the idea-pipeline system.
The CLI allows running the full pipeline from the terminal, with two modes: direct execution via flags + JSON files, and an interactive mode where the Gatekeeper generates the refinement questions and the user answers them in the terminal.

## Context
Read these files before writing any code:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

---

## What already exists — do not modify

- `IdeaPipeline.java` — fully implemented. Method: `run(RawIdea, List<RefinementQA>, Team)`
- `GatekeeperOrchestrator.java` — fully implemented. Method: `generateQuestions(RawIdea)` → `List<String>`
- All records — do not modify:
  - `RawIdea(String description)`
  - `RefinementQA(String question, String answer)`
  - `Team(List<TeamMember> members)`
  - `TeamMember(String id, String name, BaseRole baseRole, Seniority seniority, List<String> extraResponsibilities)`
  - `PipelineResult(IdeaContext context, PipelineOutput documents, ArchitectureDoc architecture, TaskGraph taskGraph, int loopsRequired)`
- Enums — do not modify:
  - `BaseRole`: FRONTEND_DEV, BACKEND_DEV, FULLSTACK_DEV, QA_ENGINEER, DEVOPS, MOBILE_DEV
  - `Seniority`: JUNIOR, MID, SENIOR, LEAD, PRINCIPAL
- `PipelineException.java` — already exists
- `ObjectMapper` — use Jackson (already on classpath via Spring Boot)
- `PipelineRunner.java` — YOUR TARGET FILE.
  Annotate with `@Component`. Implements `ApplicationRunner`.
  Inject `IdeaPipeline`, `GatekeeperOrchestrator`, `ObjectMapper` via constructor.

## Project status
- mvn clean compile → SUCCESS
- mvn test → 96/96 passing

---

## Your task

Implement `PipelineRunner.java` — one method: `run(ApplicationArguments args)`

---

## CLI interface

### Flags

| Flag | Required | Description |
|------|----------|-------------|
| `--idea` | Yes | Raw idea text as a string |
| `--team` | Yes | Path to a JSON file representing the team |
| `--interactive` | No | If present, activates interactive mode for refinements |
| `--refinements` | No (ignored if `--interactive`) | Path to a JSON file with pre-written refinements |
| `--output` | No | Path to write the `PipelineResult` JSON. Default: `pipeline-result.json` in current directory |

### team.json format
```json
{
  "members": [
    {
      "id": "tm-1",
      "name": "Alice",
      "baseRole": "BACKEND_DEV",
      "seniority": "SENIOR",
      "extraResponsibilities": []
    }
  ]
}
```

### refinements.json format (used when `--interactive` is NOT present)
```json
[
  { "question": "Who is the target user?", "answer": "Developers" },
  { "question": "What is the main constraint?", "answer": "Must be offline-first" }
]
```

---

## Execution modes

### Mode A — Direct (no `--interactive` flag)

1. Read `--idea` → `String ideaText`
2. Read `--team` → parse `team.json` → `Team`
3. Read `--refinements` → parse `refinements.json` → `List<RefinementQA>`. If flag absent, use `Collections.emptyList()`
4. Run pipeline: `ideaPipeline.run(new RawIdea(ideaText), refinements, team)`
5. Write result to stdout + output file

### Mode B — Interactive (`--interactive` flag present)

1. Read `--idea` → `String ideaText`
2. Read `--team` → parse `team.json` → `Team`
3. Call `gatekeeperOrchestrator.generateQuestions(new RawIdea(ideaText))` → `List<String> questions`
4. For each question, print to stdout and read user answer from `System.in`:
   ```
   [Question 1/5] Who is the target user?
   > _
   ```
5. Build `List<RefinementQA>` from questions + answers
6. Run pipeline: `ideaPipeline.run(new RawIdea(ideaText), refinements, team)`
7. Write result to stdout + output file

---

## Output

### Stdout summary (printed after pipeline completes)
```
=== PIPELINE COMPLETE ===
Loops required : {loopsRequired}
Total points   : {taskGraph.totalPoints()}
Tasks estimated: {taskGraph.tasks().size()}
Stack          : {architecture.stack()}
Output file    : {outputPath}
```

### Output file
Write the full `PipelineResult` as pretty-printed JSON to the path specified by `--output` (default: `pipeline-result.json`).
Use `objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result)`.

---

## Error handling

- If `--idea` is missing: print `"Error: --idea flag is required"` to stderr and exit with code 1
- If `--team` is missing: print `"Error: --team flag is required"` to stderr and exit with code 1
- If `--team` file does not exist or cannot be parsed: print `"Error: cannot read team file: {path}"` to stderr and exit with code 1
- If `--refinements` file is specified but cannot be parsed: print `"Error: cannot read refinements file: {path}"` to stderr and exit with code 1
- If `PipelineException` is thrown during execution: print `"Pipeline error [{phase}]: {message}"` to stderr and exit with code 1
- If any other exception is thrown: print `"Unexpected error: {message}"` to stderr and exit with code 1
- **Never throw exceptions out of `run()` — always catch and exit with code 1**

Use `System.exit(1)` for all error exits. Use a private helper `exitWithError(String message)` that prints to stderr and calls `System.exit(1)`.

---

## Private helpers

- `parseTeam(String path)` → `Team`: reads file at path, deserializes JSON using `objectMapper.readValue()`
- `parseRefinements(String path)` → `List<RefinementQA>`: reads file at path, deserializes JSON array
- `readAnswersFromConsole(List<String> questions)` → `List<RefinementQA>`: prints each question with index, reads answer via `Scanner(System.in)`, returns list of `RefinementQA`
- `writeResult(PipelineResult result, String outputPath)`: serializes to pretty JSON and writes to file
- `printSummary(PipelineResult result, String outputPath)`: prints the stdout summary block
- `exitWithError(String message)`: prints to `System.err` and calls `System.exit(1)`

---

## Logging
- `log.info()` on entry: mode (interactive/direct) + idea length + team file path
- `log.info()` after pipeline completes: loopsRequired + totalPoints
- `log.error()` on any caught exception before exiting

---

## Coding standards
- Constructor injection only
- `@Component` + `@Slf4j`
- No `@Autowired` on fields
- `Scanner` created once for the full interactive session, closed after all questions are answered
- `ObjectMapper` injected — do not instantiate it manually
- All file I/O via `java.nio.file.Files` or `objectMapper.readValue(new File(path), ...)`
- `System.exit()` only inside `exitWithError()` — never called directly elsewhere

---

## Testing requirements (TDD)

> Note: `PipelineRunner` uses `System.exit()` and `System.in`, which makes unit testing the full flow difficult. Tests focus on the helper methods and the pipeline invocation logic using mocks. `exitWithError()` and `System.exit()` are not directly tested.

Test file: `src/test/java/com/ideapipeline/cli/PipelineRunnerTest.java`

### Setup
Mock `IdeaPipeline`, `GatekeeperOrchestrator`, `ObjectMapper`.
Use a real `ObjectMapper` instance for JSON parsing tests.

### Tests required

- `run_shouldExecutePipelineInDirectMode`
  Provide args: `--idea=My idea`, `--team=path/to/team.json` (use a temp file with valid JSON).
  Mock `ideaPipeline.run()` to return a valid `PipelineResult`.
  Verify `ideaPipeline.run()` is called once with correct `RawIdea`.

- `run_shouldExecutePipelineWithRefinementsFile`
  Provide args: `--idea=My idea`, `--team=...`, `--refinements=path/to/refinements.json` (use temp file).
  Verify `ideaPipeline.run()` is called with non-empty `List<RefinementQA>`.

- `run_shouldUseEmptyRefinementsWhenFlagAbsent`
  Provide args: `--idea=My idea`, `--team=...` (no `--refinements`).
  Verify `ideaPipeline.run()` is called with empty `List<RefinementQA>`.

- `run_shouldExecutePipelineInInteractiveMode`
  Provide args: `--idea=My idea`, `--team=...`, `--interactive`.
  Mock `gatekeeperOrchestrator.generateQuestions()` to return `["Q1?", "Q2?"]`.
  Simulate `System.in` with answers `"A1\nA2\n"`.
  Verify `ideaPipeline.run()` is called with `List<RefinementQA>` of size 2 containing correct Q+A pairs.

- `run_shouldIgnoreRefinementsFileInInteractiveMode`
  Provide args: `--idea=My idea`, `--team=...`, `--interactive`, `--refinements=...`.
  Verify `gatekeeperOrchestrator.generateQuestions()` is called and `parseRefinements` is NOT used.

- `run_shouldWriteResultToDefaultOutputFile`
  Run in direct mode with no `--output` flag.
  Verify a file named `pipeline-result.json` is created in the current directory.
  Clean up after test.

- `run_shouldWriteResultToCustomOutputFile`
  Provide `--output=/tmp/my-result.json`.
  Verify file is created at that path.
  Clean up after test.

- `parseTeam_shouldDeserializeValidJson`
  Write valid `team.json` to temp file.
  Call `parseTeam()` directly (make it package-private for testing).
  Verify returned `Team` has correct members.

- `parseRefinements_shouldDeserializeValidJson`
  Write valid `refinements.json` to temp file.
  Call `parseRefinements()` directly.
  Verify returned list has correct `RefinementQA` entries.

- `readAnswersFromConsole_shouldBuildRefinementQAList`
  Call `readAnswersFromConsole()` directly with 2 questions.
  Simulate input `"Answer1\nAnswer2\n"` via `System.in` replacement.
  Verify returned list has 2 `RefinementQA` with correct question/answer pairs.

---

## Acceptance criteria
1. `mvn clean compile` → SUCCESS
2. `mvn test` → 10 new tests pass (PipelineRunnerTest)
3. `mvn test` → 106 total tests passing
4. `--interactive` mode calls `generateQuestions()` and reads answers from stdin
5. `--refinements` flag is ignored when `--interactive` is present
6. Default output file is `pipeline-result.json` when `--output` is not specified
7. All errors print to stderr and exit with code 1 — never throw out of `run()`
8. `Scanner` is closed after interactive session
9. `ObjectMapper` is injected, never instantiated inside the class
10. `System.exit()` is only called inside `exitWithError()`
