# Plan detallado por fase: 3a, 3b, 3.5 y 4

**Fecha:** 2026-05-25
**Versión:** 1.1

---

## Fase 3a — Critique ✅ IMPLEMENTADO (2026-05-24)

### Objetivo
Auditar `context.md`, `flows.md` y `tasks.md` para detectar inconsistencias, huecos y puntos que deban volver al debate.

### Output esperado (JSON)
```json
{
  "critiques": ["crítica 1", "crítica 2"],
  "gaps": ["gap 1"],
  "refinementNeeds": ["tema a re-debatir 1"]
}
```

### Prompt a crear
`prompts/phase-3a-critique.prompt.md`

### System prompt sugerido
> Eres un auditor de producto y arquitectura. Tu trabajo es analizar tres documentos — contexto, flujos y tareas — y encontrar inconsistencias, gaps, riesgos y temas que necesitan más debate. No eres negativo: eres riguroso. Separa tus hallazgos en critiques (problemas encontrados), gaps (lo que falta) y refinementNeeds (temas específicos que deberían volver al debate).

### Implementación esperada
- Inyectar `LlmClient` en `CriticOrchestrator`.
- Construir `systemPrompt` para auditoría documental.
- Construir `userMessage` con los 3 docs y opcionalmente historial resumido.
- Parsear JSON con `JsonParser.parse()`.
- Manejar respuestas vacías/JSON inválido con `PipelineException("Phase3a", loopCount)`.

### Tests mínimos
1. `critique_shouldReturnParsedCritiqueResult`
2. `critique_shouldThrowPipelineException_onInvalidJson`
3. `critique_shouldThrowPipelineException_onEmptyResponse`
4. `critique_shouldHandleMarkdownFencedResponse`
5. `critique_shouldIncludeAllThreeDocumentsInPrompt`

### Estados de documentos al entrar
Los 3 documentos vienen de `PipelineOutput` (Fase 2), con formato Markdown libre.

---

## Fase 3b — Validation ✅ IMPLEMENTADO (2026-05-24)

### Objetivo
Decidir si el pipeline converge o necesita loop basado en evaluación real del LLM.

### Output esperado (JSON)
```json
{
  "convergenceScore": 82,
  "shouldLoop": false,
  "reasoning": "Los documentos son coherentes entre sí. No hay gaps bloqueantes."
}
```

### Escala de convergencia
| Rango | Significado |
|-------|-------------|
| 0–40 | Loop obligatorio — inconsistencias mayores |
| 41–74 | Loop recomendado — gaps significativos |
| 75–100 | Avanzar — documentos suficientemente coherentes |

### Prompt a crear
`prompts/phase-3b-validation.prompt.md`

### System prompt sugerido
> Eres un evaluador de convergencia para un pipeline de diseño de software. Tu trabajo es leer tres documentos (contexto, flujos, tareas) y el resultado de una auditoría previa, y decidir si los documentos son suficientemente coherentes para avanzar. Asigna un convergenceScore de 0-100. Responde SOLO en JSON.

### Implementación esperada
- Inyectar `LlmClient` en `ValidationOrchestrator`.
- Enviar al LLM: los 3 docs + `CritiqueResult` + `loopCount`.
- Parsear JSON a `ValidationResult`.
- No mezclar aquí la política de `maxLoops`; eso sigue en `IdeaPipeline`.

### Tests mínimos
1. `validate_shouldReturnParsedValidationResult`
2. `validate_shouldThrowPipelineException_onInvalidJson`
3. `validate_shouldThrowPipelineException_onScoreOutOfRange`
4. `validate_shouldIncludeLoopCountInPrompt`
5. `validate_shouldNotUseDummyFormula` — verifica que el score no sea `100 - loopCount*5`

---

## Fase 3.5 — Stack Architecture ✅ IMPLEMENTADO (2026-05-24)

### Objetivo
Definir stack, servicios, restricciones y modelo de despliegue con base en documentos convergidos.

### Output esperado (JSON — recomendado para robustez)
```json
{
  "stack": "Java 21 + Spring Boot + PostgreSQL + Redis",
  "services": ["API Gateway", "Auth Service", "Core Service", "Notification Worker"],
  "constraints": ["Debe correr en Kubernetes", "Latencia < 200ms p95"],
  "deploymentModel": "Microservicios en Kubernetes con Istio",
  "rationale": "Spring Boot por familiaridad del equipo. PostgreSQL por consistencia ACID. Redis para caching de sesiones."
}
```

### Prompt a crear
`prompts/phase-3.5-stack-architecture.prompt.md`

### System prompt sugerido
> Eres un Software Architect senior. Basado en documentos de contexto, flujos y tareas de un proyecto de software, define el stack tecnológico completo. Considera escalabilidad, complejidad del equipo, time-to-market y costos. Justifica cada decisión. Responde SOLO en JSON.

### Implementación esperada
- Inyectar `LlmClient`.
- Enviar `IdeaContext` + `PipelineOutput` + puntos del debate.
- Parsear JSON a `ArchitectureDoc` record.
- Manejar null/blank/JSON inválido.

### Tests mínimos
1. `defineStack_shouldReturnParsedArchitectureDoc`
2. `defineStack_shouldThrowPipelineException_onEmptyResponse`
3. `defineStack_shouldThrowPipelineException_onInvalidJson`
4. `defineStack_shouldIncludeAllDocumentsInPrompt`

---

## Fase 4 — Planning Poker ✅ IMPLEMENTADO (2026-05-25)

### Objetivo
Transformar `tasks.md` + `ArchitectureDoc` + `Team` en un `TaskGraph` estimado.

### Decisión importante previa
Antes de implementar, fijar el **formato parseable de `tasks.md`** (ver Prioridad 4 en PLAN_SIGUIENTES_PASOS.md).

### Diseño en dos responsabilidades

#### 4A. Parseo de markdown a `Task`
- Leer `tasks.md`.
- Extraer épicas (EPIC-X), user stories (US-X), tasks (TASK-X).
- Extraer IDs, dependencias y metadatos del bloque `<!-- META: {...} -->`.

#### 4B. Estimación con LLM
- Usar `ArchitectureDoc` + `Team` + `List<Task>` como input.
- Devolver story points, votos simulados por rol y dependencias consolidadas.

### Output esperado (JSON)
```json
{
  "tasks": [
    {
      "task": { "id": "TASK-1", "title": "Crear endpoint de registro", "description": "...", "epicId": "EPIC-1", "userStory": "US-1" },
      "storyPoints": 5,
      "votes": { "BACKEND_DEV": 5, "QA_ENGINEER": 3 },
      "dependencies": ["TASK-0"]
    }
  ],
  "adjacency": { "TASK-0": ["TASK-1"] },
  "totalPoints": 21,
  "pointsByRole": { "BACKEND_DEV": 13, "QA_ENGINEER": 8 }
}
```

### Prompt a crear
`prompts/phase-4-planning-poker.prompt.md`

### System prompt sugerido
> Eres un Scrum Master que conoce el stack y el equipo. Tu trabajo es estimar un conjunto de tareas usando Planning Poker simulado. Para cada tarea, asígnale story points (Fibonacci: 1,2,3,5,8,13) desde la perspectiva de cada rol del equipo. Identifica dependencias entre tareas. Responde SOLO en JSON con la estructura de TaskGraph.

### Implementación esperada
- Agregar método `parseTasksFromMarkdown(String markdown)`.
- Definir prompt del Scrum Master / equipo simulado.
- Parsear JSON a `TaskGraph`.
- Validar consistencia de IDs y dependencias.
- Calcular o validar `totalPoints` y `pointsByRole`.

### Tests mínimos

#### Parser
1. `parseTasks_shouldExtractAllTasks`
2. `parseTasks_shouldExtractDependencies`
3. `parseTasks_shouldHandleEmptyMarkdown`
4. `parseTasks_shouldPreserveEpicAndUserStoryIds`

#### Orchestrator
1. `runPlanningPoker_shouldReturnParsedTaskGraph`
2. `runPlanningPoker_shouldThrowPipelineException_onInvalidJson`
3. `runPlanningPoker_shouldThrowPipelineException_onEmptyResponse`
4. `runPlanningPoker_shouldUseRealTeamMembers`
5. `runPlanningPoker_shouldUseArchitectureDoc`

---

## Resumen de archivos creados/modificados por fase

| Fase | Prompt (.md) | Código | Tests | Estado |
|------|-------------|--------|-------|--------|
| 3a | `prompts/phase-3a-critique.prompt.md` | `CriticOrchestrator.java` | `CriticOrchestratorTest.java` (10 tests) | ✅ |
| 3b | `prompts/phase-3b-validation.prompt.md` | `ValidationOrchestrator.java` | `ValidationOrchestratorTest.java` (12 tests) | ✅ |
| 3.5 | `prompts/phase-3.5-stack-architecture.prompt.md` | `StackArchitectOrchestrator.java` | `StackArchitectOrchestratorTest.java` (11 tests) | ✅ |
| 4 | `prompts/phase-4-planning-poker.prompt.md` | `PlanningPokerOrchestrator.java` | `PlanningPokerOrchestratorTest.java` (14 tests) | ✅ |
