# Plan de siguientes pasos

**Fecha:** 2026-05-25
**Versión:** 1.1

## Objetivo del plan
Completar el MVP funcional de IdeaPipeline manteniendo tres principios:

- **Cada fase con contrato claro.**
- **Cada implementación guiada por prompt + tests.**
- **Toda documentación persistida en archivos `.md` o `.html`.**

---

## Prioridad 0 — Alineación de base ✅ COMPLETADO (2026-05-24)

### Objetivo
Eliminar inconsistencias antes de ampliar funcionalidad.

### Entregables
- ✅ Normalizar `CONTEX.md` → `CONTEXT.md` — renombrado.
- ✅ Confirmar proveedor LLM oficial del MVP: **Ollama (qwen3.5:4b)**.
- ✅ Revisar `application.yml`, `PipelineConfig`, prompts y docs — coherentes.
- ✅ Documentar la decisión en `docs/decisions/ADR-001-llm-provider.md`.
- ✅ Versiones actualizadas en `CONTEXT.md`, `META_PIPELINE.md`, `AGENTS.md`.

### Resultado esperado
Una única fuente de verdad para:
- stack,
- proveedor LLM,
- versiones clave,
- nombre de archivos de contexto.

---

## Prioridad 1 — Implementar Fase 3a (Critique) ✅ COMPLETADO (2026-05-24)

### Objetivo
Reemplazar el critique hardcodeado por crítica real basada en LLM.

### Entregables
- ✅ `prompts/phase-3a-critique.prompt.md`
- ✅ Implementación real de `CriticOrchestrator` — constructor injection de `LlmClient`, system prompt, token budget, `extractStringList()`
- ✅ Tests unitarios: `CriticOrchestratorTest` (10 tests)

### Resultado
`CritiqueResult` real con: `critiques`, `gaps`, `refinementNeeds` desde el LLM.

---

## Prioridad 2 — Implementar Fase 3b (Validation) ✅ COMPLETADO (2026-05-24)

### Objetivo
Calcular `convergenceScore` y `shouldLoop` con evaluación real del LLM.

### Entregables
- ✅ `prompts/phase-3b-validation.prompt.md`
- ✅ Implementación real de `ValidationOrchestrator` — constructor injection de `LlmClient`, system prompt con rúbrica 0-100, validación de rango, sin fórmula dummy
- ✅ Tests unitarios: `ValidationOrchestratorTest` (12 tests)

### Resultado
`ValidationResult` coherente con los documentos generados y la crítica previa. Fórmula dummy eliminada.

---

## Prioridad 3 — Implementar Fase 3.5 (Stack Architecture) ✅ COMPLETADO (2026-05-24)

### Objetivo
Generar un `ArchitectureDoc` real a partir del contexto y docs convergidos.

### Entregables
- ✅ `prompts/phase-3.5-stack-architecture.prompt.md`
- ✅ Implementación real de `StackArchitectOrchestrator` — constructor injection de `LlmClient`, system prompt de arquitecto senior, token budget, validación de campos requeridos
- ✅ Tests unitarios: `StackArchitectOrchestratorTest` (11 tests)

### Resultado
Arquitectura consistente con el tipo de producto, restricciones, alcance y complejidad. Sin hardcodeo.

---

## Prioridad 4 — Endurecer formato de `tasks.md` ✅ COMPLETADO (2026-05-24)

### Objetivo
Hacer que `tasks.md` sea legible por humanos **y** parseable de forma robusta.

### Entregables
- ✅ `TASK_DOC_PROMPT` en `SynthesisOrchestrator.java` actualizado con IDs secuenciales, `<!-- META: {...} -->` blocks, campo de dependencias y ejemplo completo
- ✅ `docs/decisions/ADR-002-tasks-markdown-format.md` — formato canónico documentado con reglas, ejemplos y alternativas

### Formato adoptado
Markdown con metadatos embebidos en comentarios HTML:

```md
## Epic EPIC-1: Onboarding
<!-- META: {"id":"EPIC-1"} -->

### User Story US-1: Como usuario nuevo quiero registrarme
<!-- META: {"id":"US-1","epicId":"EPIC-1"} -->

#### Task TASK-1: Crear endpoint de registro
<!-- META: {"id":"TASK-1","epicId":"EPIC-1","userStory":"US-1","dependencies":[]} -->
- **Description:** Endpoint POST /api/auth/register
- **Acceptance criteria:** 
  - Debe validar email único
  - Debe hashear contraseña
- **Estimate:** M
- **Dependencies:** None
```

### Resultado
Base segura para implementar Fase 4 sin parseo frágil. IDs estables, dependencias explícitas, doble legibilidad (humana + máquina).

---

## Prioridad 5 — Implementar Fase 4 (Planning Poker) ✅ COMPLETADO (2026-05-25)

### Objetivo
Convertir `tasks.md` + `ArchitectureDoc` + `Team` en un `TaskGraph` real.

### Entregables
- ✅ `prompts/phase-4-planning-poker.prompt.md`
- ✅ Parser de `tasks.md` — `parseTasksFromMarkdown()` privado con llamada LLM (Phase 4A)
- ✅ Implementación de `PlanningPokerOrchestrator` — constructor injection de `LlmClient`, dos system prompts, cross-validation de `totalPoints`
- ✅ Tests unitarios: `PlanningPokerOrchestratorTest` (14 tests: 4 parser + 10 orchestrator)
- ✅ Documentación del `TaskGraph` esperado

### Resultado
`TaskGraph` con: tareas estimadas, dependencias explícitas, puntos totales, puntos por rol, votos. Sin hardcodeo.

---

## Prioridad 6 — Cerrar flujo API + integración end-to-end ✅ COMPLETADO (2026-05-25)

### Objetivo
Asegurar que el sistema puede ejecutarse completo desde los endpoints.

### Entregables
- ✅ Extender `RunRequest` para incluir `List<RefinementQA>` (campo `answers`).
- ✅ Corregir `PipelineController` para usar el `team` real (no dummy) y `request.answers()` real (no `List.of()`).
- ✅ Tests de controller: `PipelineControllerTest` (8 tests) — startPipeline, runPipeline con team/answers reales, null safety, error paths, health check.
- ✅ Test de integración: `IdeaPipelineIntegrationTest` (3 tests) — pipeline completo sin loop, loop de convergencia, startPipeline.

### Resultado
La API representa el pipeline real con cobertura de tests de controller e integración.

---

## Orden recomendado de ejecución

| # | Paso | Bloque |
|---|------|--------|
| ✅ 1 | Alineación base documental/técnica | Fundación |
| ✅ 2 | Prompt + implementación Fase 3a | Convergencia real |
| ✅ 3 | Prompt + implementación Fase 3b | Convergencia real |
| ✅ 4 | Prompt + implementación Fase 3.5 | Arquitectura real |
| ✅ 5 | Especificación parseable de `tasks.md` | Fundación para Fase 4 |
| ✅ 6 | Prompt + implementación Fase 4 | Planificación real |
| ✅ 7 | Ajustes de API + integración end-to-end | Cierre |
| ✅ 8 | Revisión final de documentación | Cierre |

---

## ✅ MVP completo (2026-05-25)

Las 7 fases del pipeline están implementadas con lógica real de LLM:

| Fase | Tests | Estado |
|------|-------|--------|
| 0 — Gatekeeper | 8 | ✅ |
| 1 — Debate | 7 | ✅ |
| 2 — Synthesis | 8 | ✅ |
| 3a — Critique | 10 | ✅ |
| 3b — Validation | 12 | ✅ |
| 3.5 — Stack Architecture | 11 | ✅ |
| 4 — Planning Poker | 14 | ✅ |
| PipelineControllerTest | 8 | ✅ |
| IdeaPipelineIntegrationTest | 3 | ✅ |
| LlmClientImpl | 6 | ✅ |
| **Total** | **87** | **MVP funcional** |

Pendiente para producción: revisión final de docs.

---

## Cronograma lógico por bloques

### Bloque A — Convergencia real ✅
- Fase 3a (Critique) ✅
- Fase 3b (Validation) ✅

### Bloque B — Arquitectura real ✅
- Fase 3.5 (Stack Architecture) ✅

### Bloque C — Planificación ejecutiva real ✅
- Endurecer `tasks.md` ✅
- Fase 4 (Planning Poker) ✅

### Bloque D — Cierre de producto técnico ✅
- API ✅
- Integración ✅
- Documentación final ✅

---

## Definición de terminado del MVP

### ✅ Definición de terminado del MVP — CUMPLIDA (2026-05-25)

- ✅ Las 7 fases ejecutan lógica real LLM.
- ✅ El loop de convergencia ya no depende de placeholders.
- ✅ `ArchitectureDoc` se genera desde contexto real.
- ✅ `PlanningPokerOrchestrator` estima tareas reales parseadas desde markdown endurecido.
- ✅ La API usa datos reales del request (`team` y `answers`).
- ✅ Existe cobertura de tests unitarios + controller + integración (87 tests).
- ✅ Tests de integración end-to-end.
- ✅ La documentación principal está alineada con el código.
