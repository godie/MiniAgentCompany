# ADR-002: Formato parseable de tasks.md

**Estado:** Aceptado
**Fecha:** 2026-05-24
**Decisores:** Codebuff (asistente)

---

## Contexto

La Fase 2 (Synthesis) genera `tasks.md` como documento Markdown de forma libre. La Fase 4 (Planning Poker) necesita parsear ese documento para extraer `Task` objects con IDs estables, descripciones, épicas, user stories y dependencias.

El formato original era demasiado laxo para parseo robusto:
- Sin IDs estables ni secuenciales
- Sin campo de dependencias
- Estructura inconsistente entre ejecuciones

---

## Decisión

Adoptar un formato **Markdown enriquecido con metadatos HTML** (`<!-- META: {...} -->`) que sea:

1. **Legible por humanos** — el Markdown se ve bien en cualquier visor
2. **Parseable por máquina** — los bloques META contienen JSON con IDs y dependencias
3. **Parseable por LLM** — incluso sin el parser de META comments, un LLM puede extraer la estructura del Markdown

### Formato canónico

```markdown
## Epic EPIC-1: [Epic Name]
<!-- META: {"id":"EPIC-1"} -->

### User Story US-1: [User Story Title]
<!-- META: {"id":"US-1","epicId":"EPIC-1"} -->

#### Task TASK-1: [Task Title]
<!-- META: {"id":"TASK-1","epicId":"EPIC-1","userStory":"US-1","dependencies":[]} -->
- **Description:** [1-2 sentence description]
- **Acceptance criteria:**
  - [criterion 1]
  - [criterion 2]
- **Estimate:** S | M | L
- **Dependencies:** None | TASK-X, TASK-Y
```

### Reglas

| Regla | Detalle |
|-------|---------|
| IDs secuenciales | EPIC-1, EPIC-2, ... / US-1, US-2, ... / TASK-1, TASK-2, ... |
| META obligatorio | Cada heading (Epic, User Story, Task) debe tener su bloque `<!-- META: {...} -->` |
| Dependencias | Array de IDs en META + campo `- **Dependencies:**` legible |
| Estimate | S (Small), M (Medium), L (Large) — para referencia humana |
| Jerarquía | Epics contienen User Stories, User Stories contienen Tasks |

---

## Consecuencias

### Positivas
- El parser de Fase 4 (LLM-based) puede extraer tasks con IDs y dependencias de forma fiable
- Los META comments son invisibles en renderizado Markdown pero parseables
- Si el parser LLM falla, un parser de regex simple puede extraer los bloques META como fallback
- El formato es autodocumentado: cualquier desarrollador puede leerlo y entender la estructura

### Negativas
- El prompt de Fase 2 es más largo (incluye ejemplo completo)
- Depende de que el LLM de síntesis siga el formato estrictamente
- Los META comments añaden ruido visual en editores de texto plano (pero son invisibles en renderizado)

---

## Alternativas consideradas

### 1. JSON puro en vez de Markdown
**Rechazado** porque `tasks.md` también debe ser legible para el orquestador humano en el flujo manual.

### 2. Front matter YAML
**Rechazado** porque requeriría un parser YAML adicional y no es estándar en el ecosistema del proyecto.

### 3. Tablas Markdown
**Rechazado** porque las tablas no escalan bien con acceptance criteria multilínea y se vuelven ilegibles.

### 4. Parseo exclusivo por LLM sin formato fijo
**Rechazado** porque el LLM de parseo puede alucinar IDs o perder dependencias si el formato es ambiguo.

---

## Implementación

- **Fase 2** (`SynthesisOrchestrator.TASK_DOC_PROMPT`): actualizado para generar este formato
- **Fase 4** (`PlanningPokerOrchestrator`): usará un LLM parser que entiende este formato + fallback de regex para META comments

---

*Fin del ADR.*
