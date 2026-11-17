# Systematic workflow: prompt → implementation → test → documentation

**Date:** 2026-05-25
**Version:** 1.2

## Objective
Define a repeatable way to move forward phase by phase without losing consistency between code, prompts and documentation.

---

## 1. Recommended documentation structure

```
idea-pipeline/
├── prompts/
│   ├── phase-0-gatekeeper.prompt.md          ✅ Exists
│   ├── phase-1-debate.prompt.md              ✅ Exists
│   ├── phase-2-synthesis.prompt.md           ✅ Exists
│   ├── llm-client.prompt.md                  ✅ Exists
│   ├── engineer-lead.prompt.md               ✅ Exists
│   ├── phase-3a-critique.prompt.md           ✅ Created
│   ├── phase-3b-validation.prompt.md         ✅ Created
│   ├── phase-3.5-stack-architecture.prompt.md ✅ Created
│   └── phase-4-planning-poker.prompt.md      ✅ Created
└── docs/
    ├── REVISION_PROYECTO.md                  ✅ Created
    ├── PLAN_SIGUIENTES_PASOS.md              ✅ Created
    ├── PLAN_FASES_3A_4.md                    ✅ Created
    ├── WORKFLOW_PROMPTS_IMPLEMENTACION.md    ✅ Created
    ├── phases/
    │   ├── PHASE-3A-SPEC.md                  ✅ Created (2026-05-25)
    │   ├── PHASE-3B-SPEC.md                  ✅ Created (2026-05-25)
    │   ├── PHASE-3.5-SPEC.md                 ✅ Created (2026-05-25)
    │   └── PHASE-4-SPEC.md                   ✅ Created (2026-05-25)
    ├── reports/
    │   ├── PHASE-3A-IMPLEMENTATION-REPORT.md ✅ Created (2026-05-25)
    │   ├── PHASE-3B-IMPLEMENTATION-REPORT.md ✅ Created (2026-05-25)
    │   ├── PHASE-3.5-IMPLEMENTATION-REPORT.md ✅ Created (2026-05-25)
    │   └── PHASE-4-IMPLEMENTATION-REPORT.md  ✅ Created (2026-05-25)
    └── decisions/
        ├── ADR-001-llm-provider.md           ✅ Created (2026-05-24)
        └── ADR-002-tasks-markdown-format.md  ✅ Created (2026-05-24)
```

---

## 2. Recommended sequence per phase

For every pending phase, always repeat this cycle:

### 2.1 Spec
- Define the phase objective.
- List the expected inputs.
- Specify the outputs (exact format).
- Enumerate the expected errors.
- Set the acceptance criteria.

### 2.2 Prompt
- Write the implementation prompt in `.md`.
- List the files that already exist and must not be modified.
- Indicate the files to modify.
- Define the required system prompts.
- Set the required tests.
- Set the validation commands (`mvn clean compile`, `mvn test`).

### 2.3 Implementation
- Modify only the files of the phase.
- Do not touch stable contracts (records, enums) unless there is a documented decision.
- Follow the project standards: `@Service`, `@Slf4j`, constructor injection, `StructuredTaskScope`.
- Handle errors with `PipelineException`.

### 2.4 Testing
- Unit tests first (Mockito, `@ExtendWith(MockitoExtension.class)`).
- Then integration tests if applicable.
- Record the results.
- Verify that `mvn test` passes.

### 2.5 Closure documentation
- Write the implementation report in `docs/reports/`.
- Record the decisions taken in `docs/decisions/` if applicable.
- Note follow-ups if any remain pending.

---

## 3. Minimum template for future prompts

Every `phase-X-*.prompt.md` file should include these sections:

```markdown
# phase-X-name.prompt.md

## Role
What role the implementing agent adopts.

## Context
What files it must read before touching code.

## What already exists
What pieces it must not modify (list them explicitly).

## Your task
What class or classes it must implement.

## Required behavior
Exact rules of the method or flow. Include system prompts as constants.

## Error handling
How to wrap exceptions and which `PipelineException` to use.

## Testing requirements
Mandatory tests with method names and cases to cover.

## Acceptance criteria
Verifiable checklist (compiles, tests pass, no UnsupportedOperationException, etc.).

## Files to modify
Closed list of allowed files.

## Implementation hints
Code patterns, reference snippets, conventions.
```

---

## 4. Rules to keep consistency

### 4.1 Records are the source of truth
If a prompt contradicts a Java record, the Java record wins or an explicit change is documented before implementing.

### 4.2 LLM outputs must be easy to parse
Whenever the result is going to be mapped to code:
- **Prefer JSON** for machine-to-machine exchange.
- **Use Markdown** when the output is documentation for humans.
- If a markdown must be parsed later, give it a strict grammar.

### 4.3 Every phase must leave evidence in `.md`
When completing a phase, generate at least:
- A spec (`docs/phases/PHASE-X-SPEC.md`).
- An implementation report (`docs/reports/PHASE-X-IMPLEMENTATION-REPORT.md`).
- A decision note if there were contract changes (`docs/decisions/ADR-XXX.md`).

### 4.4 The pipeline must not depend on silent placeholders
If a phase is still a mock or dummy, it must be explicitly documented.

### 4.5 Code conventions
- Constructor injection only — never `@Autowired` on fields.
- `@Service` + `@Slf4j` in all orchestrators.
- `StructuredTaskScope.ShutdownOnFailure` for parallelism (not `CompletableFuture`).
- `@Component` for client implementations.
- Exceptions always wrapped in `PipelineException`.

---

## 5. Quality checklist per phase

Before considering a phase finished, verify:

- [ ] The prompt exists in `prompts/`.
- [ ] The spec exists in `docs/phases/`.
- [ ] The implementation removes the placeholders of that phase.
- [ ] There are new or updated unit tests.
- [ ] `mvn clean compile` passes without errors.
- [ ] `mvn test` passes (all tests, not only the new ones).
- [ ] There is an implementation report in `docs/reports/`.
- [ ] No ambiguous contracts were left undocumented.
- [ ] There is no `UnsupportedOperationException` in the implemented methods.
- [ ] Logs are at adequate levels (`info` for input/output, `warn` for anomalies, `error` for failures).

---

## 6. Step-by-step workflow for the following phases

| Step | Action | Output |
|------|--------|--------|
| ✅ 1 | Create Phase 3a spec | `docs/phases/PHASE-3A-SPEC.md` |
| ✅ 2 | Create Phase 3a prompt | `prompts/phase-3a-critique.prompt.md` |
| ✅ 3 | Implement + test Phase 3a | `CriticOrchestrator.java` + tests |
| ✅ 4 | Create Phase 3b spec | `docs/phases/PHASE-3B-SPEC.md` |
| ✅ 5 | Create Phase 3b prompt | `prompts/phase-3b-validation.prompt.md` |
| ✅ 6 | Implement + test Phase 3b | `ValidationOrchestrator.java` + tests |
| ✅ 7 | Create Phase 3.5 spec | `docs/phases/PHASE-3.5-SPEC.md` |
| ✅ 8 | Create Phase 3.5 prompt | `prompts/phase-3.5-stack-architecture.prompt.md` |
| ✅ 9 | Implement + test Phase 3.5 | `StackArchitectOrchestrator.java` + tests |
| ✅ 10 | Create the tasks.md format ADR | `docs/decisions/ADR-002-tasks-markdown-format.md` |
| ✅ 11 | Create Phase 4 spec | `docs/phases/PHASE-4-SPEC.md` |
| ✅ 12 | Create Phase 4 prompt | `prompts/phase-4-planning-poker.prompt.md` |
| ✅ 13 | Implement parser + orchestrator Phase 4 | `PlanningPokerOrchestrator.java` + tests |
| ✅ 14 | Fix the API (`RunRequest`, `PipelineController`) | `RunRequest.java`, `PipelineController.java` |
| ✅ 15 | End-to-end integration test | Integration test |

---

## 7. Final recommendation

The safest combination for this project is:

- **Markdown** for human documentation (prompts, specs, reports, docs).
- **JSON** for structured LLM responses (easy to parse with `JsonParser`).
- **Markdown with HTML metadata** (`<!-- META: {...} -->`) when a human document must be parsed later (such as `tasks.md` in Phase 4).

This approach keeps traceability, automation and good readability without mixing responsibilities.
