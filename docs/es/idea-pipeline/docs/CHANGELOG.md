# CHANGELOG

**Versión del changelog:** 1.0  
**Proyecto:** Idea Pipeline  
**Fecha de última entrada:** 2026-05-25  

Registro cronológico de todas las fases implementadas del pipeline, desde la alineación base hasta el cierre del MVP.  
Basado en los reports de `docs/reports/` y el plan `docs/PLAN_SIGUIENTES_PASOS.md`.

---

## Anterior a 2026-05-24 — Fundación del pipeline

### Phases 0–2 + Infraestructura (pre-report tracking)

**Qué existía antes de iniciar el tracking por reports:**

- `GatekeeperOrchestrator` — Fase 0: preguntas de refinamiento + enriquecimiento de contexto (8 tests)
- `DebateOrchestrator` — Fase 1: debate multi-agente con `StructuredTaskScope` (7 tests)
- `SynthesisOrchestrator` — Fase 2: síntesis paralela de 3 documentos (8 tests)
- `LlmClientImpl` — Cliente LLM con `chat()` y `chatWithHistory()` (6 tests)
- `JsonParser` — Extracción tolerante de JSON desde respuestas Markdown
- `PipelineProperties` + `PipelineConfig` — Configuración YAML y beans Spring
- `PipelineException` — Excepción checked con metadata de fase y loop
- Modelos de datos (`RawIdea`, `IdeaContext`, `DebateMessage`, `PipelineOutput`, `TaskGraph`, etc.)
- `IdeaPipeline` — Orquestador central con loop de convergencia (placeholders en fases 3a–4)

**Tests:** 29/29 ✅

---

## 2026-05-24 — Bloque A: Convergencia real

### Phase 3a — Critique (2026-05-24)

**Reporte:** [`docs/reports/PHASE-3A-IMPLEMENTATION-REPORT.md`](reports/PHASE-3A-IMPLEMENTATION-REPORT.md)  
**Spec:** [`docs/phases/PHASE-3A-SPEC.md`](phases/PHASE-3A-SPEC.md)  
**Prompt:** [`prompts/phase-3a-critique.prompt.md`](../prompts/phase-3a-critique.prompt.md)

**Qué cambió:** Se reemplazó el `CritiqueResult` hardcodeado por auditoría real basada en LLM.

- `CriticOrchestrator.java` — reescrito con inyección de `LlmClient`, system prompt de auditor, token budget (5 + 10 sobre 30 mensajes), `extractStringList()` tolerante
- `CriticOrchestratorTest.java` — 10 tests (parseo, errores, markdown fences, truncamiento, campos faltantes)
- Resultado: `CritiqueResult` real con `critiques`, `gaps`, `refinementNeeds` desde el LLM

**Tests:** 39/39 (29 previos + 10 nuevos) ✅

---

### Phase 3b — Validation (2026-05-24)

**Reporte:** [`docs/reports/PHASE-3B-IMPLEMENTATION-REPORT.md`](reports/PHASE-3B-IMPLEMENTATION-REPORT.md)  
**Spec:** [`docs/phases/PHASE-3B-SPEC.md`](phases/PHASE-3B-SPEC.md)  
**Prompt:** [`prompts/phase-3b-validation.prompt.md`](../prompts/phase-3b-validation.prompt.md)

**Qué cambió:** Se eliminó la fórmula dummy `score = max(10, 100 - loopCount * 5)` y se implementó evaluación real con rúbrica 0–100.

- `ValidationOrchestrator.java` — reescrito con rúbrica de 4 niveles, `loopCount` explícito, validación de rango post-parseo
- `ValidationOrchestratorTest.java` — 12 tests (score bajo → loop, score fuera de rango, verificación `chat()` llamado, markdown fences)
- `maxLoops` se mantiene como política en `IdeaPipeline`, no en el orchestrator
- Resultado: `ValidationResult` coherente con los documentos y la crítica previa

**Tests:** 51/51 (39 previos + 12 nuevos) ✅

---

## 2026-05-24 — Bloque B: Arquitectura real

### Phase 3.5 — Stack Architecture (2026-05-24)

**Reporte:** [`docs/reports/PHASE-3.5-IMPLEMENTATION-REPORT.md`](reports/PHASE-3.5-IMPLEMENTATION-REPORT.md)  
**Spec:** [`docs/phases/PHASE-3.5-SPEC.md`](phases/PHASE-3.5-SPEC.md)  
**Prompt:** [`prompts/phase-3.5-stack-architecture.prompt.md`](../prompts/phase-3.5-stack-architecture.prompt.md)

**Qué cambió:** Se reemplazó el `ArchitectureDoc` hardcodeado por definición real de stack vía LLM.

- `StackArchitectOrchestrator.java` — reescrito con system prompt de arquitecto senior, 5 campos, validación de `stack` + `services` requeridos, `constraints` opcional
- `StackArchitectOrchestratorTest.java` — 11 tests (5 campos, errores, markdown fences, stack/services vacíos, constraints ausente)
- Resultado: `ArchitectureDoc` con stack, servicios, restricciones, deployment y rationale

**Tests:** 62/62 (51 previos + 11 nuevos) ✅

---

## 2026-05-24 — Fundación para Fase 4

### ADR-002: Formato canónico de `tasks.md` (2026-05-24)

**ADR:** [`docs/decisions/ADR-002-tasks-markdown-format.md`](decisions/ADR-002-tasks-markdown-format.md)

**Qué cambió:** Se endureció el formato de `tasks.md` para hacerlo parseable de forma robusta:

- IDs secuenciales: `EPIC-1`, `US-1`, `TASK-1`
- Bloques de metadatos: `<!-- META: {"id":"TASK-1","epicId":"EPIC-1","dependencies":["TASK-2"]} -->`
- Campo `Dependencies` explícito
- `TASK_DOC_PROMPT` en `SynthesisOrchestrator.java` actualizado con ejemplo completo

**Razón:** El parser de Fase 4 necesita IDs estables y dependencias explícitas; sin este formato, el parseo sería frágil.

---

## 2026-05-25 — Bloque C: Planificación ejecutiva real

### Phase 4 — Planning Poker (2026-05-25)

**Reporte:** [`docs/reports/PHASE-4-IMPLEMENTATION-REPORT.md`](reports/PHASE-4-IMPLEMENTATION-REPORT.md)  
**Spec:** [`docs/phases/PHASE-4-SPEC.md`](phases/PHASE-4-SPEC.md)  
**Prompt:** [`prompts/phase-4-planning-poker.prompt.md`](../prompts/phase-4-planning-poker.prompt.md)  
**Prerrequisito:** [`docs/decisions/ADR-002-tasks-markdown-format.md`](decisions/ADR-002-tasks-markdown-format.md)

**Qué cambió:** Se reemplazó el `TaskGraph` hardcodeado por planificación real con dos llamadas LLM consecutivas.

- `PlanningPokerOrchestrator.java` — reescrito con dos fases:
  - **Fase 4A:** `parseTasksFromMarkdown()` — parser LLM de Markdown a JSON
  - **Fase 4B:** `buildPlanningPokerUserMessage()` — Scrum Master simula Planning Poker con Fibonacci
- Cross-validation de `totalPoints` (warning no bloqueante)
- Validación de `taskId` contra tareas parseadas
- Markdown vacío → `TaskGraph` vacío (no excepción)
- `PlanningPokerOrchestratorTest.java` — 14 tests (4 parser + 10 orchestrator)
- Resultado: `TaskGraph` con estimaciones Fibonacci, dependencias, votos, puntos por rol

**Tests:** 76/76 (62 previos + 14 nuevos) ✅

---

## 2026-05-25 — Bloque D: Cierre de producto técnico

### Controller + Integración (2026-05-25)

**Qué cambió:** Se cerró el gap entre la API y el pipeline real.

- `RunRequest` extendido con campo `answers` (`List<RefinementQA>`)
- `PipelineController` modificado para usar `request.team()` y `request.answers()` reales (no dummies)
- `PipelineControllerTest.java` — 8 tests (startPipeline, runPipeline con team/answers reales + null safety + error, health check)
- `IdeaPipelineIntegrationTest.java` — 3 tests @SpringBootTest (pipeline completo sin loop, loop de convergencia, startPipeline)

**Tests:** **87/87** ✅ (76 previos + 11 nuevos)

---

### Documentación post-MVP (2026-05-25)

**Qué cambió:** Revisión de consistencia en 7 archivos de documentación.

- `META_PIPELINE.md` — v1.4→v1.5, fecha sección 11 → 2026-05-25
- `CONTEXT.md` — sección 10 reescrita (de "proof of concept" a tabla MVP con 87 tests)
- `AGENTS.md` — fecha → 2026-05-25
- `REVISION_PROYECTO.md` — sección 1 reescrita (de "pipeline no completo" a "completo end-to-end")
- `PLAN_SIGUIENTES_PASOS.md` — paso 8 ✅, Bloque D ✅
- `WORKFLOW_PROMPTS_IMPLEMENTACION.md` — árbol de docs actualizado, todos los pasos ✅
- `PLAN_FASES_3A_4.md` — Fase 4 ⏳→✅ con label IMPLEMENTADO

---

### Documentación de fases (2026-05-25)

**Qué cambió:** Se crearon specs e informes para todas las fases 3a–4.

- `docs/phases/PHASE-3A-SPEC.md` — especificación detallada
- `docs/phases/PHASE-3B-SPEC.md` — especificación detallada
- `docs/phases/PHASE-3.5-SPEC.md` — especificación detallada
- `docs/phases/PHASE-4-SPEC.md` — especificación detallada
- `docs/reports/PHASE-3A-IMPLEMENTATION-REPORT.md` — reporte de implementación
- `docs/reports/PHASE-3B-IMPLEMENTATION-REPORT.md` — reporte de implementación
- `docs/reports/PHASE-3.5-IMPLEMENTATION-REPORT.md` — reporte de implementación
- `docs/reports/PHASE-4-IMPLEMENTATION-REPORT.md` — reporte de implementación

---

### Javadoc (2026-05-25)

**Qué cambió:** Se añadió Javadoc a 13 clases públicas del pipeline.

- 7 orchestrators, `LlmClientImpl`, `IdeaPipeline`, `PipelineController`, `PipelineConfig`, `PipelineProperties`, `PipelineException`
- `@param`, `@return`, `@throws`, `{@link}` cross-references entre fases

---

### README.md (2026-05-25)

**Qué cambió:** Se creó el `README.md` del proyecto con quick start, API reference, configuración, fases y guía de contribución.

---

### CHANGELOG.md (2026-05-25)

**Este archivo** — registro cronológico consolidado.

---

## Resumen del MVP

| Fecha | Hito | Tests acumulados |
|-------|------|:---:|
| < 2026-05-24 | Fases 0–2 + LlmClient (fase fundacional) | 29 |
| 2026-05-24 | Fase 3a — Critique | 39 |
| 2026-05-24 | Fase 3b — Validation | 51 |
| 2026-05-24 | Fase 3.5 — Stack Architecture | 62 |
| 2026-05-24 | ADR-002 — Formato `tasks.md` | 62 |
| 2026-05-25 | Fase 4 — Planning Poker | 76 |
| 2026-05-25 | Controller + Integración | **87** |
| 2026-05-25 | Documentación post-MVP, Javadoc, README | **87** |

**MVP completo:** Las 7 fases del pipeline implementadas con lógica real de LLM. Loop de convergencia funcional. API end-to-end. 87 tests pasando.

---

*Fin del changelog.*
