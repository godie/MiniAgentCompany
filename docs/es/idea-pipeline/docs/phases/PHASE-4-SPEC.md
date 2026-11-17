# PHASE-4-SPEC: Planning Poker (Estimación de Tareas)

**Fecha:** 2026-05-25
**Versión:** 1.0
**Estado:** ✅ IMPLEMENTADO

---

## 1. Objetivo

Transformar `tasks.md` (documento Markdown en formato endurecido con META blocks) + `ArchitectureDoc` + `Team` en un `TaskGraph` completamente estimado. Esta es la fase final del pipeline.

La fase tiene **dos responsabilidades**:
- **4A — Parseo**: Extraer `Task` objects del Markdown vía LLM
- **4B — Planning Poker**: Estimar story points, votos por miembro, dependencias y roles vía LLM

---

## 2. Inputs

| Input | Tipo | Fuente | Descripción |
|-------|------|--------|-------------|
| `tasksMarkdown` | `String` | Fase 2 (Synthesis) | Documento tasks.md en formato endurecido (ADR-002) |
| `team` | `Team` | Request del usuario | Equipo con miembros, roles, seniority |
| `architecture` | `ArchitectureDoc` | Fase 3.5 | Stack, servicios, constraints, deployment |

---

## 3. Formato de tasks.md (ADR-002)

El documento sigue un formato endurecido con:

- IDs secuenciales: `EPIC-1`, `US-1`, `TASK-1`
- Bloques `<!-- META: {"id":"TASK-1","epicId":"EPIC-1","userStory":"US-1","dependencies":[]} -->`
- Campos: Description, Acceptance criteria, Estimate (S|M|L), Dependencies

Ver `docs/decisions/ADR-002-tasks-markdown-format.md` para el formato canónico completo.

---

## 4. Output: Fase 4A (Parseo)

`List<Task>` — extraído del Markdown vía LLM parser:

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

> **Nota:** El campo `sizeEstimate` (S|M|L) es retornado por el LLM parser como contexto para la Fase 4B, pero NO se almacena en el record `Task` (cuyos campos son: `id`, `title`, `description`, `epicId`, `userStory`). Se usa únicamente como referencia para la estimación.

---

## 5. Output: Fase 4B (Planning Poker)

`TaskGraph` — record con 4 campos:

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

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `tasks` | `List<EstimatedTask>` | Tareas estimadas con votes, storyPoints, dependencies |
| `adjacency` | `Map<String, List<String>>` | Grafo de dependencias (DAG, sin ciclos) |
| `totalPoints` | `int` | Suma total de story points (cross-validado) |
| `pointsByRole` | `Map<String, Integer>` | Suma de story points agrupada por `BaseRole` |

### Reglas de estimación

- **Fibonacci**: 1, 2, 3, 5, 8, 13, 21
- **Story points** = COMPLEXITY + EFFORT + UNCERTAINTY
- **Dependencia**: TASK-A depende de TASK-B si no puede INICIAR hasta que TASK-B esté DONE
- **Seniority**: Mayor seniority → estimaciones más bajas
- **QA**: Estima más alto que devs (incluye testing)
- **Consenso**: `storyPoints` final = mediana de votos redondeada al Fibonacci más cercano

---

## 6. Comportamiento

### 6.1 Flujo principal

1. Validar inputs: `tasksMarkdown` no null, `team` no null/vacío, `architecture` no null
2. **Fase 4A**: `parseTasksFromMarkdown(tasksMarkdown)` → `List<Task>`
   - Si markdown vacío → `TaskGraph` vacío temprano
   - Llamar LLM parser con `TASK_PARSE_SYSTEM_PROMPT`
   - Extraer `Task` objects del JSON
3. **Fase 4B**: Planning Poker
   - Construir `userMessage` con team, architecture, y lista de tareas
   - Llamar LLM con `PLANNING_POKER_SYSTEM_PROMPT`
   - Parsear `EstimatedTask[]`, `adjacency`, `totalPoints`, `pointsByRole`
   - Cross-validar `totalPoints` contra suma computada (warning si mismatch)
   - Validar que cada `taskId` en la respuesta existe en `parsedTasks`
4. Retornar `TaskGraph`

### 6.2 System Prompts

- **Parser**: Actúa como parser preciso. Extrae tasks, epics, user stories del Markdown.
- **Planner**: Actúa como Scrum Master facilitando Planning Poker. Estima desde la perspectiva de CADA miembro del equipo.

---

## 7. Manejo de errores

| Condición | Excepción | Phase |
|-----------|-----------|-------|
| `tasksMarkdown == null` | `PipelineException("tasksMarkdown is null", "Phase4", 0)` | Phase4 |
| `team == null \|\| team.members().isEmpty()` | `PipelineException("team is null or empty", "Phase4", 0)` | Phase4 |
| `architecture == null` | `PipelineException("architecture is null", "Phase4", 0)` | Phase4 |
| Markdown vacío | `TaskGraph` vacío (no excepción) | — |
| Respuesta parser vacía | `PipelineException("Empty response from task parser LLM", "Phase4", 0)` | Phase4 |
| Respuesta poker vacía | `PipelineException("Empty response from planning poker LLM", "Phase4", 0)` | Phase4 |
| JSON inválido (parser) | `PipelineException("Failed to parse tasks from markdown", "Phase4", 0, cause)` | Phase4 |
| JSON inválido (poker) | `PipelineException("Planning poker failed", "Phase4", 0, cause)` | Phase4 |
| `taskId` desconocido en respuesta | `PipelineException("Unknown task ID in LLM response: X", "Phase4", 0)` | Phase4 |
| `totalPoints` mismatch | ⚠️ Warning log (no excepción) | — |

---

## 8. Criterios de aceptación

- [x] `PlanningPokerOrchestrator` usa `LlmClient` inyectado por constructor
- [x] Sin tareas ni estimaciones hardcodeadas — todo del LLM
- [x] Dos system prompts como `private static final String`
- [x] `parseTasksFromMarkdown()` es método privado (separation of concerns)
- [x] Markdown vacío → `TaskGraph` vacío (no excepción)
- [x] Team members, roles, seniority, extraResponsibilities en el prompt
- [x] Architecture (stack, services, constraints, deployment) en el prompt
- [x] Cross-validation de `totalPoints` con warning
- [x] IDs desconocidos → `PipelineException`
- [x] `mvn compile` exitoso
- [x] 14 tests unitarios pasan (`PlanningPokerOrchestratorTest`)

---

## 9. Tests

Ver `PlanningPokerOrchestratorTest.java` (14 tests):

### Parser (4 tests)

| # | Test | Qué verifica |
|---|------|-------------|
| 1 | `parseTasks_shouldExtractAllTasks` | Extrae 2 tareas del Markdown |
| 2 | `parseTasks_shouldPreserveEpicAndUserStoryIds` | IDs EPIC-1, US-1, TASK-1 preservados |
| 3 | `parseTasks_shouldHandleEmptyMarkdown` | String vacío → TaskGraph vacío, sin llamar LLM |
| 4 | `parseTasks_shouldThrowPipelineException_onInvalidParseJson` | JSON inválido del parser → excepción |

### Orchestrator (10 tests)

| # | Test | Qué verifica |
|---|------|-------------|
| 5 | `runPlanningPoker_shouldReturnParsedTaskGraph` | TaskGraph completo: tasks, adjacency, totalPoints, pointsByRole, votes |
| 6 | `runPlanningPoker_shouldThrowPipelineException_onInvalidJson` | JSON inválido del poker → excepción |
| 7 | `runPlanningPoker_shouldThrowPipelineException_onEmptyResponse` | Respuesta vacía del poker → excepción |
| 8 | `runPlanningPoker_shouldUseRealTeamMembers` | Nombres, roles, seniority en userMessage |
| 9 | `runPlanningPoker_shouldUseArchitectureDoc` | Stack, services, deployment, constraints en userMessage |
| 10 | `runPlanningPoker_shouldThrowPipelineException_onEmptyTeam` | Team vacío → excepción |
| 11 | `runPlanningPoker_shouldThrowPipelineException_onNullTasksMarkdown` | null markdown → excepción |
| 12 | `runPlanningPoker_shouldThrowPipelineException_onUnknownTaskId` | TASK-999 no existe → excepción |
| 13 | `runPlanningPoker_shouldHandleMarkdownFencedResponse` | JSON entre ```json fences |
| 14 | `runPlanningPoker_shouldLogWarningOnTotalPointsMismatch` | totalPoints=100, suma=8 → warning, resultado retornado |

---

## 10. Archivos involucrados

| Archivo | Rol |
|---------|-----|
| `PlanningPokerOrchestrator.java` | Implementación (2 fases LLM) |
| `PlanningPokerOrchestratorTest.java` | 14 tests unitarios |
| `TaskGraph.java` | Record de output (no modificado) |
| `EstimatedTask.java` | Record de output (no modificado) |
| `Task.java` | Record interno (no modificado) |
| `Team.java` / `TeamMember.java` | Records de input (no modificados) |
| `ArchitectureDoc.java` | Record de input (no modificado) |
| `ADR-002-tasks-markdown-format.md` | ADR del formato parseable |

---

*Fin del spec.*
