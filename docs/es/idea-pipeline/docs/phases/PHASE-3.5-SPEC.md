# PHASE-3.5-SPEC: Stack Architecture (Definición de Arquitectura)

**Fecha:** 2026-05-24
**Versión:** 1.0
**Estado:** ✅ IMPLEMENTADO

---

## 1. Objetivo

Definir el stack tecnológico completo, topología de servicios, restricciones no funcionales y modelo de despliegue basado en los documentos convergidos. Esta fase se ejecuta **después** de que la Fase 3b determina convergencia.

---

## 2. Inputs

| Input | Tipo | Fuente | Descripción |
|-------|------|--------|-------------|
| `context` | `IdeaContext` | Fase 0 (Gatekeeper) | Contexto enriquecido de la idea original |
| `documents` | `PipelineOutput` | Fase 2 (Synthesis) | Tres documentos convergidos |
| `debateHistory` | `List<DebateMessage>` | Fase 1 (Debate) | Historial del debate multi-agente |

---

## 3. Output

`ArchitectureDoc` — record con 5 campos:

```json
{
  "stack": "Java 21 + Spring Boot + PostgreSQL + Redis",
  "services": ["API Gateway", "Auth Service", "Core Service"],
  "constraints": ["Kubernetes", "p95 < 200ms"],
  "deploymentModel": "Microservicios en Kubernetes con Istio",
  "rationale": "Spring Boot por familiaridad del equipo. PostgreSQL por ACID."
}
```

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `stack` | `String` | ✅ No blank | Stack tecnológico principal (lenguaje, framework, DB, message broker, caching, monitoring, CI/CD) |
| `services` | `List<String>` | ✅ No vacío | Lista de servicios/módulos desplegables (3-8 items) |
| `constraints` | `List<String>` | Opcional | Restricciones no funcionales (3-8 items, default `[]`) |
| `deploymentModel` | `String` | ✅ | Modelo de despliegue y orquestación |
| `rationale` | `String` | ✅ | Justificación de decisiones (máx 250 palabras) |

---

## 4. Comportamiento

### 4.1 System Prompt

El LLM actúa como **Software Architect senior (15+ años)**. Debe considerar:
- Requisitos de escalabilidad implícitos en los flujos
- Complejidad del equipo (preferir tecnología "aburrida")
- Trade-offs time-to-market vs mantenibilidad
- Costos (infraestructura, licencias, overhead operacional)
- Compatibilidad con ecosistema existente

### 4.2 Flujo principal

1. Validar que `context`, `documents` y `debateHistory` no sean `null`
2. Construir `userMessage` con:
   - Idea context (enriched summary)
   - Tres documentos (context.md, flows.md, tasks.md)
   - Historial de debate formateado
3. Llamar al LLM con `ARCHITECT_SYSTEM_PROMPT`
4. Parsear respuesta JSON
5. Validar campos requeridos: `stack` no blank, `services` no vacío
6. Campos opcionales (`constraints`) defaultean a `[]`
7. Retornar `ArchitectureDoc`

### 4.3 Token Budget

Misma estrategia que Fase 3a: si `debateHistory.size() > 30`, truncar a primeros 5 + últimos 10.

---

## 5. Manejo de errores

| Condición | Excepción | Phase | LoopCount |
|-----------|-----------|-------|-----------|
| `context == null` | `PipelineException("context is null", "Phase3.5", 0)` | Phase3.5 | 0 |
| `documents == null` | `PipelineException("documents is null", "Phase3.5", 0)` | Phase3.5 | 0 |
| `debateHistory == null` | `PipelineException("debateHistory is null", "Phase3.5", 0)` | Phase3.5 | 0 |
| Respuesta LLM vacía/null | `PipelineException("Empty response from LLM", "Phase3.5", 0)` | Phase3.5 | 0 |
| `stack` vacío en respuesta | `PipelineException("Empty stack in architecture response", "Phase3.5", 0)` | Phase3.5 | 0 |
| `services` vacío en respuesta | `PipelineException("Empty services list in architecture response", "Phase3.5", 0)` | Phase3.5 | 0 |
| JSON inválido | `PipelineException("Architecture definition failed", "Phase3.5", 0, cause)` | Phase3.5 | 0 |
| Error del LLM | `PipelineException("Architecture definition failed", "Phase3.5", 0, cause)` | Phase3.5 | 0 |

---

## 6. Criterios de aceptación

- [x] `StackArchitectOrchestrator` usa `LlmClient` inyectado por constructor
- [x] Sin datos hardcodeados — toda la arquitectura viene del LLM
- [x] System prompt definido como `private static final String`
- [x] Campos requeridos (`stack`, `services`) validados post-parseo
- [x] `constraints` ausente → lista vacía (no null)
- [x] Los 3 documentos + idea context + debate history en el prompt
- [x] Token budget para debate histories grandes
- [x] `mvn compile` exitoso
- [x] 11 tests unitarios pasan (`StackArchitectOrchestratorTest`)

---

## 7. Tests

Ver `StackArchitectOrchestratorTest.java` (11 tests):

| # | Test | Qué verifica |
|---|------|-------------|
| 1 | `defineStack_shouldReturnParsedArchitectureDoc` | Parseo de los 5 campos |
| 2 | `defineStack_shouldThrowPipelineException_onInvalidJson` | JSON inválido → excepción |
| 3 | `defineStack_shouldThrowPipelineException_onEmptyResponse` | Respuesta vacía → excepción |
| 4 | `defineStack_shouldThrowPipelineException_onEmptyStack` | Stack vacío → excepción |
| 5 | `defineStack_shouldThrowPipelineException_onEmptyServices` | Services vacío → excepción |
| 6 | `defineStack_shouldIncludeAllDocumentsInPrompt` | Los 3 docs + enrichedSummary en prompt |
| 7 | `defineStack_shouldIncludeDebateHistoryInPrompt` | Formato `agentName (round N): content` |
| 8 | `defineStack_shouldHandleMarkdownFencedResponse` | JSON entre ```json fences |
| 9 | `defineStack_shouldHandleMissingConstraints` | Constraints ausente → [] |
| 10 | `defineStack_shouldThrowPipelineException_onLlmFailure` | RuntimeException → PipelineException |
| 11 | `defineStack_shouldHandleEmptyDebateHistory` | `List.of()` → "(No debate history available)" |

---

## 8. Archivos involucrados

| Archivo | Rol |
|---------|-----|
| `StackArchitectOrchestrator.java` | Implementación |
| `StackArchitectOrchestratorTest.java` | 11 tests unitarios |
| `ArchitectureDoc.java` | Record de output (no modificado) |
| `IdeaContext.java` | Record de input (no modificado) |
| `PipelineOutput.java` | Record de input (no modificado) |

---

*Fin del spec.*
