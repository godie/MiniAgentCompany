# META_PIPELINE.md — Pipeline de Debate y Convergencia entre LLMs

**Versión:** 1.2
**Última actualización:** 2026-04-10
**Basado en:** diseño convergido manualmente (score 93) y Engineer Lead prompt v1.1

---

## 1. Propósito del documento

Este archivo es la **fuente de verdad** para el sistema multi‑agente. Define:

- La dualidad **Generativo vs Ejecutivo** del pipeline.
- Las fases y su flujo.
- El **formato canónico de intercambio** entre agentes (versión 1.0).
- Los roles y responsabilidades de cada LLM.
- Los criterios de convergencia y salida.

---

## 2. Dualidad fundamental: Generativo vs Ejecutivo

| Naturaleza | Fases | Objetivo | Paralelismo | Planificación |
|------------|-------|----------|-------------|----------------|
| **Generativo** | 0, 1, 2, 3a, 3b, 3.5 | Refinar una idea hasta obtener documentos coherentes y un stack técnico. | Bajo (debate secuencial, síntesis en paralelo solo en automático). | Se itera hasta convergencia (máx. 3 loops). |
| **Ejecutivo** | 4 | Generar tareas concretas, estimaciones y asignaciones para agentes de código. | Alto (simulación de equipo, planificación poker). | Se ejecuta una vez, sobre documentos ya convergidos. |

**Regla de oro:**
> No se salta de la fase 3.5 a la 4 mientras el `convergenceScore` < 75 o no se haya agotado el número máximo de loops.

---

## 3. Fases del pipeline

| Fase | Nombre | Naturaleza | Input | Output | Criterio de salida |
|------|--------|------------|-------|--------|--------------------|
| 0 | Gatekeeper | Generativo | `RawIdea` | `IdeaContext` enriquecido | Usuario responde las preguntas de refinamiento |
| 1 | Debate | Generativo | `IdeaContext` + historial vacío | `List<DebateMessage>` (rondas) | Número fijo de rondas (ej. 2‑3) o agotamiento de gaps |
| 2 | Document Synthesis | Generativo | Historial de debate + `IdeaContext` | 3 docs: `context.md`, `flows.md`, `tasks.md` | Documentos generados (sin validación aún) |
| 3a | Critique | Generativo | Los 3 docs + historial | `CritiqueResult` (critiques, gaps, refinementNeeds) | Críticas generadas |
| 3b | Validation + Convergence | Generativo | Los 3 docs + `CritiqueResult` + `loopCount` | `ValidationResult` (convergenceScore, shouldLoop, reasoning) | `convergenceScore >= 75` o máximo 3 loops |
| 3.5 | Stack Architecture | Generativo | `IdeaContext` + 3 docs + historial | `ArchitectureDoc` | Documento generado |
| 4 | Planning Poker | Ejecutivo | `tasks.md` + `ArchitectureDoc` + `Team` | `TaskGraph` | Todas las tareas estimadas y grafo generado |

---

## 4. Modelos de datos (records Java 21)

### Idea domain
- `RawIdea(String description)`
- `RefinementQA(String question, String answer)`
- `IdeaContext(String rawIdea, List<RefinementQA> refinements, String enrichedSummary)`

### Debate domain
- `DebateMessage(String agentName, String content, int round)`
- `PipelineOutput(String contextDocument, String flowDocument, String taskDocument)`

### Validation domain (separado)
- `CritiqueResult(List<String> critiques, List<String> gaps, List<String> refinementNeeds)`
- `ValidationResult(int convergenceScore, boolean shouldLoop, String reasoning)`

### Architecture domain
- `ArchitectureDoc(String stack, List<String> services, List<String> constraints, String deploymentModel, String rationale)`

### Team domain
- `TeamMember(String id, String name, BaseRole baseRole, Seniority seniority, List<String> extraResponsibilities)`
- `Team(List<TeamMember> members)` — helpers: `byRole(BaseRole)`, `leads()`
- `BaseRole` enum: FRONTEND_DEV, BACKEND_DEV, FULLSTACK_DEV, QA_ENGINEER, DEVOPS, MOBILE_DEV
- `Seniority` enum: JUNIOR, MID, SENIOR, LEAD, PRINCIPAL

### Planning domain
- `Task(String id, String title, String description, String epicId, String userStory)`
- `EstimatedTask(Task task, int storyPoints, Map<String, Integer> votes, List<String> dependencies)`
- `TaskGraph(List<EstimatedTask> tasks, Map<String, List<String>> adjacency, int totalPoints, Map<String, Integer> pointsByRole)`

### Pipeline state & result
- `PipelineState(IdeaContext context, List<DebateMessage> debateHistory, PipelineOutput output, int loopCount)`
- `PipelineResult(IdeaContext context, PipelineOutput documents, ArchitectureDoc architecture, TaskGraph taskGraph, int loopsRequired)`

---

## 5. Formato canónico de intercambio (v1.0)

Toda comunicación entre fases debe seguir esta plantilla:

```
---
PHASE: [número y nombre]
AGENT: [rol del agente que recibe]
FROM: [rol del agente que envía]
---

SUMMARY:
[2-5 líneas máximo — qué se decidió/generó en la fase anterior]

CONTEXT_FILES:
[lista de archivos de referencia, no se repite su contenido]

INPUT:
[el dato concreto que necesita este agente para trabajar]

TASK:
[instrucción clara y única — qué debe producir]

OUTPUT_FORMAT:
[formato exacto esperado: JSON / Markdown / record]
---
```

**Reglas adicionales:**
- Los archivos listados en `CONTEXT_FILES` deben estar disponibles localmente.
- Si `INPUT` es demasiado largo, truncar con un resumen y la nota `[TRUNCATED – ver archivo historial.txt]`.
- Para documentos largos, usar etiquetas como `<!-- CONTEXT_DOC --> ... <!-- END_CONTEXT_DOC -->`.

---

## 6. Roles en la ejecución (prueba manual)

| Rol | Agente LLM | Responsabilidad |
|-----|------------|-----------------|
| **Orquestador / PO** | Humano | Tiene la idea, decide qué avanza, copia contexto, evalúa outputs, da el go/no‑go. |
| **Architect + Gatekeeper** | Claude | Diseño de arquitectura, generación de prompts, refinamiento con preguntas. |
| **CriticAgent (Fase 3a)** | DeepSeek | Revisa prompts, documentos y decisiones buscando huecos, problemas y riesgos. |
| **ValidationAgent (Fase 3b)** | ChatGPT | Toma el output del crítico + los documentos y da el veredicto de convergencia. |

> En las fases 1 y 2, los roles de ProductAgent, ArchitectAgent y CriticAgent son interpretados por los mismos LLMs según el system prompt. En la fase 4, el Scrum Master LLM puede ser cualquiera (recomendado: Claude).

---

## 7. Criterios de convergencia

- **Convergencia de diseño** (del pipeline): `convergenceScore >= 80` y `shouldLoop = false`.
- **Convergencia de instancia** (para una idea concreta): `convergenceScore >= 75`.

**Máximo de loops en fase 3b:** 3. Si después de 3 loops el score sigue <75, el pipeline se detiene y se reporta "convergencia forzada".

**Fórmula de `convergenceScore` (orientativa):**
- 0–40: loop obligatorio (inconsistencias mayores)
- 41–74: loop recomendado (gaps significativos)
- 75–100: avanzar (documentos suficientemente coherentes)

---

## 8. Archivos de contexto compartidos

| Archivo | Propósito |
|---------|-----------|
| `META_PIPELINE.md` | Este archivo — fuente de verdad del pipeline y formato canónico |
| `CONTEXT.md` | Descripción general del proyecto, stack, decisiones de diseño |
| `AGENTS.md` | Definición de cada agente, su personalidad y system prompt |
| `idea.md` | La idea concreta que se está refinando |
| `log.md` | Bitácora de decisiones del orquestador humano |

---

## 9. Flujo de trabajo manual paso a paso

1. **Inicio** – El Orquestador escribe una `RawIdea` en `idea.md`.
2. **Fase 0** – Copia `idea.md` a Claude con el prompt de Gatekeeper. Claude devuelve preguntas. El Orquestador responde.
3. **Fase 1** – El Orquestador envía el `IdeaContext` enriquecido y el historial a los tres LLMs en paralelo, usando el formato canónico. Recolecta respuestas, consolida y repite rondas.
4. **Fase 2** – Envía el historial completo a un SynthesisAgent (Claude o ChatGPT) para generar los 3 docs.
5. **Fase 3a** – Pasa los 3 docs a DeepSeek (CriticAgent) para obtener `CritiqueResult`.
6. **Fase 3b** – Pasa los 3 docs + `CritiqueResult` a ChatGPT (ValidationAgent) para obtener `ValidationResult` y decisión de loop.
7. **Si loop** – Enriquece `IdeaContext` con los `gaps` y `refinementNeeds` y vuelve a Fase 1.
8. **Si convergido** – Avanza a Fase 3.5 (Stack Architecture con Claude).
9. **Fase 4** – Configura el equipo (archivo `team.json`) y ejecuta Planning Poker con un Scrum Master LLM sobre `tasks.md`.

---

## 10. Decisiones de diseño clave

- **Virtual threads + StructuredTaskScope** sobre CompletableFuture: más limpio, manejo de fallos automático con `ShutdownOnFailure`.
- **Stack definido antes de Planning Poker**: evita estimaciones vagas.
- **Dos firmas en `LlmClient`**: `chat()` para fases sin historial, `chatWithHistory()` para debate.
- **Separación de Critique y Validation**: roles y modelos distintos (DeepSeek critica, ChatGPT valida).
- **Planning Poker simplificado**: un Scrum Master LLM simula al equipo completo; evita cientos de llamadas.
- **Formato canónico de intercambio**: asegura reproducibilidad en la prueba manual.

---

## 11. Próximos pasos (estado actual)

- ✅ `META_PIPELINE.md` (v1.2) — generado y corregido.
- ✅ `idea.md` — generada y aprobada (score 96).
- ✅ `CONTEXT.md` — generado (pendiente de aprobación final).
- ✅ `AGENTS.md` — generado (pendiente de aprobación final).
- ⏳ Engineer Lead prompt — listo para ejecutar con un code agent, una vez que `CONTEXT.md` y `AGENTS.md` estén aprobados.

---

*Fin del documento.*
```

---
