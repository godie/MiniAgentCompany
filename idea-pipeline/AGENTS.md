# AGENTS.md — idea-pipeline

**Versión:** 1.2
**Última actualización:** 2026-04-18
**Basado en:** diseño convergido manualmente (score 95)

## Formato de cada agente

---
### [Nombre]
**Fase**: número y nombre
**Rol**: una oración
**Modelo recomendado**: LLM sugerido
**Input**: qué recibe
**Output**: qué produce y en qué formato
**Paralelismo**: solo / paralelo con otros / secuencial
**System prompt summary**: filosofía del prompt
---

---

### GatekeeperAgent
**Fase**: 0 — Refinement
**Rol**: Transforma una idea cruda en contexto enriquecido haciendo las preguntas mínimas necesarias.
**Modelo recomendado**: Claude
**Input**: RawIdea (texto libre)
**Output**: Lista de 5-7 preguntas en JSON — `{"questions": ["..."]}`
**Paralelismo**: Solo
**System prompt summary**: Product strategist experto en convertir ideas vagas en specs claras.
Hace las preguntas mínimas y más importantes. Máximo 6 preguntas.
Cada pregunta desambigua algo crítico. Responde solo en JSON.

---

### ContextEnricherAgent
**Fase**: 0 — Context Building
**Rol**: Genera el resumen ejecutivo enriquecido a partir de la idea y las respuestas del usuario.
**Modelo recomendado**: Claude
**Input**: RawIdea + List<RefinementQA>
**Output**: String — resumen ejecutivo de máximo 200 palabras
**Paralelismo**: Solo, secuencial después de GatekeeperAgent
**System prompt summary**: Sintetiza idea original y respuestas de refinamiento en un resumen
que captura esencia, problema, usuario objetivo y restricciones clave.
Es el contexto base que todas las fases siguientes reciben.

---

### ProductAgent
**Fase**: 1 — Debate
**Rol**: Defiende la perspectiva de producto, usuario y valor de negocio.
**Modelo recomendado**: Cualquier LLM capaz de mantener un rol
**Input**: IdeaContext + snapshot del historial de debate
**Output**: DebateMessage — máximo 3 oraciones por turno
**Paralelismo**: Paralelo con ArchitectAgent y CriticAgent dentro de cada ronda
**System prompt summary**: Product Manager senior enfocado en el usuario, mercado y experiencia.
Pregunta sobre el para quién y el por qué. Referencia a los otros agentes por nombre.
Conciso y concreto.

---

### ArchitectAgent
**Fase**: 1 — Debate
**Rol**: Evalúa viabilidad técnica, stack, datos e integraciones.
**Modelo recomendado**: Cualquier LLM con buen razonamiento técnico
**Input**: IdeaContext + snapshot del historial de debate
**Output**: DebateMessage — máximo 3 oraciones por turno
**Paralelismo**: Paralelo con ProductAgent y CriticAgent dentro de cada ronda
**System prompt summary**: Software Architect enfocado en viabilidad técnica y escalabilidad.
Cuestiona complejidad innecesaria. Referencia a los otros agentes por nombre.
Conciso y concreto.

---

### CriticAgent (debate)
**Fase**: 1 — Debate
**Rol**: Devil's advocate — busca riesgos, suposiciones no validadas y problemas no evidentes.
**Modelo recomendado**: DeepSeek
**Input**: IdeaContext + snapshot del historial de debate
**Output**: DebateMessage — máximo 3 oraciones por turno
**Paralelismo**: Paralelo con ProductAgent y ArchitectAgent dentro de cada ronda
**System prompt summary**: Pensador crítico que busca riesgos legales, de adopción y monetización.
No es negativo, es riguroso. Referencia a los otros agentes por nombre.
Fuerza al equipo a validar suposiciones.

---

### ContextDocSynthesizer
**Fase**: 2 — Document Synthesis
**Rol**: Genera context.md con visión, problema, usuarios, alcance y restricciones.
**Modelo recomendado**: Claude
**Input**: IdeaContext + DebateHistory
**Output**: Markdown — secciones: Visión, Problema, Usuarios objetivo,
Alcance, Restricciones, Supuestos, Glosario
**Paralelismo**: Paralelo con FlowDocSynthesizer y TaskDocSynthesizer
**System prompt summary**: Technical writer que produce documentación clara y precisa.
No inventa — solo sintetiza lo que surgió del debate.

---

### FlowDocSynthesizer
**Fase**: 2 — Document Synthesis
**Rol**: Genera flows.md con flujos de usuario, edge cases y diagramas Mermaid.
**Modelo recomendado**: Claude o ChatGPT
**Input**: IdeaContext + DebateHistory
**Output**: Markdown — secciones: Flujos principales, Flujos alternativos,
Edge cases, Diagramas Mermaid
**Paralelismo**: Paralelo con ContextDocSynthesizer y TaskDocSynthesizer
**System prompt summary**: Business analyst que documenta comportamiento del sistema.
Incluye happy paths y casos borde identificados en el debate.
Usa Mermaid para diagramas.

---

### TaskDocSynthesizer
**Fase**: 2 — Document Synthesis
**Rol**: Genera tasks.md con Epics, User Stories y tareas técnicas con estimaciones S/M/L.
**Modelo recomendado**: Claude
**Input**: IdeaContext + DebateHistory
**Output**: Markdown — estructura: Epics → User Stories → Tasks técnicas,
cada task con descripción, criterios de aceptación y estimación S/M/L
**Paralelismo**: Paralelo con ContextDocSynthesizer y FlowDocSynthesizer
**System prompt summary**: Tech lead que descompone trabajo en tareas accionables.
Prioriza por valor y dependencias. No genera tareas sin criterios de aceptación.

---

### ValidationCriticAgent
**Fase**: 3a — Critique
**Rol**: Audita los 3 documentos buscando inconsistencias, gaps y temas a re-debatir.
**Modelo recomendado**: DeepSeek
**Input**: PipelineOutput (3 docs) + DebateHistory
**Output**: JSON — `{"critiques": [], "gaps": [], "refinementNeeds": []}`
**Paralelismo**: Solo
**System prompt summary**: Auditor de producto y arquitectura. Evalúa calidad y consistencia
de los documentos en contexto del debate que los produjo. No evalúa convergencia — solo
identifica problemas. Distingue entre problemas bloqueantes y mejoras incrementales.
El historial de debate se incluye en el mensaje al LLM para contextualizar las críticas.

---

### ValidationAgent
**Fase**: 3b — Validation + Convergence
**Rol**: Decide si el pipeline converge o hace loop basándose en docs y críticas.
**Modelo recomendado**: ChatGPT
**Input**: PipelineOutput (3 docs) + ValidationResult de fase 3a + loopCount
**Output**: JSON — `{"convergenceScore": 0-100, "shouldLoop": boolean, "reasoning": "..."}`
**Paralelismo**: Solo, secuencial después de ValidationCriticAgent
**System prompt summary**: Evaluador que determina convergencia del pipeline.
convergenceScore 0-40: loop obligatorio. 41-74: loop recomendado. 75-100: avanzar.
Considera el loopCount para evitar ciclos infinitos.

---

### StackArchitectAgent
**Fase**: 3.5 — Stack Architecture
**Rol**: Define el stack tecnológico completo basado en los documentos convergidos.
**Modelo recomendado**: Claude
**Input**: IdeaContext + PipelineOutput + DebateHistory
**Output**: Markdown — secciones: Stack Decision, Services & Components,
Deployment Model, Technical Constraints, Rationale
**Paralelismo**: Solo
**System prompt summary**: Software Architect senior que toma decisiones de stack
considerando escalabilidad, complejidad del equipo, time-to-market y costos.
Justifica cada decisión. No elige tecnología por moda.

---

### ScrumMasterAgent
**Fase**: 4 — Planning Poker
**Rol**: Simula al equipo completo estimando tareas con contexto técnico real.
**Modelo recomendado**: Claude
**Input**: List<Task> + ArchitectureDoc + Team
**Output**: JSON — TaskGraph con storyPoints, votes y dependencies por tarea
**Paralelismo**: Solo (simula paralelismo internamente)
**System prompt summary**: Scrum Master que conoce el stack y el equipo.
Estima cada tarea desde la perspectiva de cada rol del equipo.
Usa escala Fibonacci. Identifica dependencias entre tareas.
Justifica estimaciones altas con riesgos técnicos concretos.

---

### IdeaPipeline (meta-agente orquestador)
**Fase**: Todas
**Rol**: Coordina el flujo completo entre fases, gestiona el loop de convergencia
y decide cuándo avanzar.
**Modelo recomendado**: N/A — es código Java, no un LLM
**Input**: RawIdea + List<RefinementQA> + Team + debateRounds
**Output**: PipelineResult completo
**Paralelismo**: Orquesta paralelismo interno en fases 1, 2 y 4
**System prompt summary**: No aplica. Su lógica es determinista:
ejecuta fases en orden, evalúa convergenceScore después de 3b,
hace loop enriqueciendo el contexto con gaps si shouldLoop=true,
fuerza avance después de maxLoops.

---

## Diagrama de interacción entre agentes

```
[Usuario / Orquestador humano]
         |
         | RawIdea
         ↓
[GatekeeperAgent] ──preguntas──→ [Usuario]
         |                            |
         |←────────── respuestas ─────┘
         |
[ContextEnricherAgent]
         |
         | IdeaContext
         ↓
┌────────────────────────────────────┐
│  FASE 1 — por ronda (paralelo)     │
│  [ProductAgent]                    │
│  [ArchitectAgent]  → snapshot      │
│  [CriticAgent]                     │
└─────────────┬──────────────────────┘
              | DebateHistory
              ↓
┌────────────────────────────────────┐
│  FASE 2 (paralelo)                 │
│  [ContextDocSynthesizer]           │
│  [FlowDocSynthesizer]              │
│  [TaskDocSynthesizer]              │
└─────────────┬──────────────────────┘
              | PipelineOutput (3 docs)
              ↓
[ValidationCriticAgent] — Fase 3a
              |
              | critiques + gaps
              ↓
[ValidationAgent] — Fase 3b
              |
         convergenceScore
         /              \
    < 75                >= 75
    shouldLoop=true     shouldLoop=false
       |                    |
       | enrich context      |
       └──→ FASE 1 (loop)   ↓
                    [StackArchitectAgent] — Fase 3.5
                            |
                            | ArchitectureDoc
                            ↓
                    [ScrumMasterAgent] — Fase 4
                            |
                            ↓
                       TaskGraph
```

## Loop de convergencia

**Qué lo dispara**: convergenceScore < 75 en Fase 3b Y loopCount < maxLoops (3)

**Qué se lleva al loop**: IdeaContext enriquecido con los gaps y refinementNeeds
identificados por ValidationCriticAgent. El historial anterior NO se descarta —
se añade como contexto adicional.

**Qué constituye convergencia**: convergenceScore >= 75. El sistema no requiere
score perfecto — 75 indica que los documentos son suficientemente coherentes
para definir el stack y estimar tareas.

**Fuerza avance**: Si loopCount >= 3 independientemente del score.
El pipeline reporta "convergencia forzada" en PipelineResult.

## Añadir un nuevo agente

1. Definir su rol, fase, input y output en este archivo.
2. Crear su system prompt en el orchestrator correspondiente como constante privada.
3. Si es una nueva fase: crear su orchestrator en `com.ideapipeline.orchestrator`.
4. Wirear en `IdeaPipeline.java` en el orden correcto.
5. Actualizar el diagrama de interacción en este archivo.
6. Actualizar META_PIPELINE.md si la fase nueva cambia el flujo.
