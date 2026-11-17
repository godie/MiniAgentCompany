# CONTEXT.md — idea-pipeline

**Versión:** 1.3
**Última actualización:** 2026-05-25
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
y Ollama (qwen3.5:4b) como ValidationAgent.

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
- Spring Boot 4.1.0-SNAPSHOT
- Spring AI 2.0.0-SNAPSHOT (Ollama provider — qwen3.5:4b)
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
Fase 3a produce críticas.
Fase 3b evalúa convergencia.
Son responsabilidades distintas que usan el mismo modelo (qwen3.5:4b) con distintos system prompts.

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

✅ **MVP completo** — Las 7 fases del pipeline tienen implementación real con LLM (Ollama/qwen3.5:4b).

| Fase | Orquestador | Tests |
|------|-------------|-------|
| 0 | GatekeeperOrchestrator | 8 |
| 1 | DebateOrchestrator | 7 |
| 2 | SynthesisOrchestrator | 8 |
| 3a | CriticOrchestrator | 10 |
| 3b | ValidationOrchestrator | 12 |
| 3.5 | StackArchitectOrchestrator | 11 |
| 4 | PlanningPokerOrchestrator | 14 |
| Controller + Integración | PipelineControllerTest + IdeaPipelineIntegrationTest | 11 |
| Cliente LLM | LlmClientImpl | 6 |
| **Total** | | **87** |

Pendiente: tests de integración con Ollama real (los 3 tests actuales usan @MockitoBean LlmClient).
