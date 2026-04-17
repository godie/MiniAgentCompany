# CONTEXT.md — idea-pipeline

**Versión:** 1.1
**Última actualización:** 2026-04-10
**Basado en:** diseño convergido manualmente (score 91)

## 1. Qué es

Sistema multi-agente de debate y convergencia que transforma ideas crudas
de software en proyectos estructurados y listos para ejecutar.

Orquesta múltiples LLMs con roles especializados para producir —
de forma sistemática, trazable y reproducible — los artefactos necesarios
para iniciar el desarrollo de un producto de software.

## 2. Problema que resuelve

Pasar de una idea vaga a un proyecto con documentos de contexto, flujos,
tareas estimadas y stack definido requiere múltiples sesiones, criterios
dispersos y decisiones no documentadas.

Este sistema hace ese proceso explícito, iterativo y convergente.

## 3. Naturaleza del sistema

El sistema es recursivo: fue diseñado usando sus propias fases de forma
manual, con Claude como Architect+Gatekeeper, DeepSeek como CriticAgent
y ChatGPT como ValidationAgent.

## 4. Dualidad fundamental

| Naturaleza | Fases | Qué produce |
|------------|-------|-------------|
| Generativa | 0, 1, 2, 3a, 3b, 3.5 | Documentos que convergen |
| Ejecutiva | 4 | Tareas listas para agentes de código |

Las fases generativas se iteran hasta convergenceScore >= 75 o max 3 loops.
La fase ejecutiva corre una sola vez sobre documentos ya convergidos.

## 5. Fases

| Fase | Nombre | Input | Output |
|------|--------|-------|--------|
| 0 | Gatekeeper | RawIdea | IdeaContext enriquecido |
| 1 | Debate | IdeaContext | List<DebateMessage> |
| 2 | Synthesis | DebateHistory + IdeaContext | context.md + flows.md + tasks.md |
| 3a | Critique | 3 docs + historial | critiques + gaps + refinementNeeds |
| 3b | Validation | 3 docs + output 3a | ValidationResult (convergenceScore + shouldLoop) |
| 3.5 | Stack Architecture | IdeaContext + 3 docs | ArchitectureDoc |
| 4 | Planning Poker | tasks.md + ArchitectureDoc + Team | TaskGraph |

## 6. Stack técnico

- Java 21
- Spring Boot 3.3+
- Spring AI 0.8.1 (OpenAI provider — gpt-4o-mini)
- Virtual threads + StructuredTaskScope (Java 21 structured concurrency)
- Maven
- Lombok

## 7. Decisiones de diseño clave

**Virtual threads sobre CompletableFuture**
El pipeline es I/O-bound. Virtual threads con StructuredTaskScope
son más limpios, más legibles y el fallo de un subtask cancela los demás
automáticamente con ShutdownOnFailure.

**Stack definido ANTES de Planning Poker**
Sin stack definido, las estimaciones de tareas son vagas.
El ArchitectAgent define el stack en Fase 3.5 para que el
Planning Poker tenga contexto técnico real.

**Dos firmas en LlmClient**
chat() para fases sin historial (síntesis, validación, arquitectura).
chatWithHistory() para fases con historial acumulado (debate).

**Convergencia automática**
El sistema decide si hacer loop basándose en convergenceScore,
no en un límite fijo. Si después de 3 loops el score no alcanza
75, se fuerza avance — evita loops infinitos.

**CriticOrchestrator separado de ValidationOrchestrator**
Fase 3a (DeepSeek) produce críticas.
Fase 3b (ChatGPT) evalúa convergencia.
Son responsabilidades distintas y modelos distintos.

**Planning Poker simplificado**
Un Scrum Master LLM simula al equipo completo.
Evita 100+ llamadas por ejecución manteniendo el valor
de las estimaciones contextualizadas.

## 8. Estructura de paquetes

```
com.ideapipeline
├── config/
├── controller/
│   └── dto/
├── model/
│   └── enums/
├── client/
├── orchestrator/
├── pipeline/
└── exception/
```

## 9. Archivos de contexto del proyecto

| Archivo | Propósito |
|---------|-----------|
| META_PIPELINE.md | Fuente de verdad del pipeline y formato canónico |
| CONTEXT.md | Este archivo — arquitectura y decisiones |
| AGENTS.md | Definición de cada agente |
| idea.md | La idea concreta que originó el sistema |
| log.md | Bitácora de decisiones del orquestador |

## 10. Estado actual

Prueba de concepto manual completada.
Diseño convergido con score 93 (ValidationAgent — ChatGPT).
Engineer Lead prompt v1.1 aprobado con score 91.
Siguiente paso: generación del skeleton del proyecto.
