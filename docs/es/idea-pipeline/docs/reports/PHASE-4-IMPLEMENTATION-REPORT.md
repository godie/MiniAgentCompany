# PHASE-4-IMPLEMENTATION-REPORT: Planning Poker

**Fecha:** 2026-05-25
**Versión:** 1.0

---

## Resumen

Fase 4 implementada exitosamente. `PlanningPokerOrchestrator` transforma `tasks.md` + `ArchitectureDoc` + `Team` en un `TaskGraph` completamente estimado usando dos llamadas LLM consecutivas: un parser de Markdown (Fase 4A) y una sesión simulada de Planning Poker (Fase 4B). Reemplaza el placeholder hardcodeado anterior.

---

## Lo que se hizo

### Implementación

- **`PlanningPokerOrchestrator.java`**: Reescrito completamente
  - Inyección de `LlmClient` por constructor
  - **Dos system prompts** como constantes:
    - `TASK_PARSE_SYSTEM_PROMPT` — parser preciso de Markdown a JSON
    - `PLANNING_POKER_SYSTEM_PROMPT` — Scrum Master facilitando Planning Poker con Fibonacci
  - Método público `runPlanningPoker(String tasksMarkdown, Team team, ArchitectureDoc architecture)`
  - Método privado `parseTasksFromMarkdown(String)` — Fase 4A
  - Método privado `buildPlanningPokerUserMessage(...)` — Fase 4B
  - **Cross-validation** de `totalPoints`: compara el valor declarado por el LLM vs suma computada de `storyPoints` (warning si mismatch)
  - Validación de IDs: cada `taskId` en la respuesta del LLM debe existir en las tareas parseadas
  - Markdown vacío → `TaskGraph` vacío (no excepción)
  - Manejo de `votes`, `dependencies`, `adjacency`, `pointsByRole` desde JSON del LLM

### Prerrequisito: ADR-002

Antes de implementar Fase 4, se endureció el formato de `tasks.md`:
- IDs secuenciales: `EPIC-1`, `US-1`, `TASK-1`
- Bloques `<!-- META: {"id":"...","dependencies":[...]} -->`
- Campo `Dependencies` explícito
- Ver `docs/decisions/ADR-002-tasks-markdown-format.md`

### Tests

- **`PlanningPokerOrchestratorTest.java`**: 14 tests unitarios
  - **4 parser tests**: extracción de tasks, preservación de IDs, markdown vacío, JSON inválido
  - **10 orchestrator tests**: TaskGraph completo, JSON inválido, respuesta vacía, team members en prompt, architecture en prompt, team vacío, null markdown, unknown taskId, markdown fences, totalPoints mismatch

---

## Decisiones tomadas

| Decisión | Razón |
|----------|-------|
| Dos fases LLM (parse + estimate) | Separación de concerns; el parser no debe estimar, el estimador no debe parsear |
| Markdown vacío → `TaskGraph` vacío | No es un error; puede ocurrir si Synthesis no generó tareas |
| `totalPoints` cross-validation no bloqueante | Solo warning; el LLM puede tener error de suma pero las estimaciones individuales son correctas |
| Fibonacci (1,2,3,5,8,13,21) | Estándar de la industria para Planning Poker |
| `pointsByRole` usa `BaseRole` enum values | Consistente con el modelo de datos del proyecto |
| `adjacency` como DAG | Representa dependencias entre tareas; el LLM no debe producir ciclos |

---

## Resultados de tests

```
PlanningPokerOrchestratorTest: 14/14 ✅
Total suite: 76/76 ✅ (sin regresiones)
```

Posteriormente con tests de controller + integración: **87/87 ✅**

---

## Issues conocidos

| Issue | Severidad | Estado |
|-------|-----------|--------|
| `totalPoints` puede no coincidir con suma de storyPoints | Baja | Solo warning; no bloquea el pipeline |
| El parser LLM puede fallar con Markdown muy no-estándar | Media | Mitigado por ADR-002 (formato endurecido) |

---

## Follow-ups

- [x] Tests de controller (`PipelineControllerTest`) — 8 tests
- [x] Tests de integración (`IdeaPipelineIntegrationTest`) — 3 tests
- [x] Cierre de API gap (`RunRequest.answers`)
- [x] Documentación final post-MVP

---

*Fin del reporte.*
