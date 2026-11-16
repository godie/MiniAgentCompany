# PHASE-3A-SPEC: Critique (Auditoría Documental)

**Fecha:** 2026-05-24
**Versión:** 1.0
**Estado:** ✅ IMPLEMENTADO

---

## 1. Objetivo

Auditar los tres documentos sintetizados (`context.md`, `flows.md`, `tasks.md`) para detectar inconsistencias, gaps y temas que deban volver al debate. Esta fase es la primera del loop de convergencia (3a → 3b → posible loop a Fase 1).

---

## 2. Inputs

| Input | Tipo | Fuente | Descripción |
|-------|------|--------|-------------|
| `docs` | `PipelineOutput` | Fase 2 (Synthesis) | Tres documentos: `contextDocument`, `flowDocument`, `taskDocument` en formato Markdown |
| `debateHistory` | `List<DebateMessage>` | Fase 1 (Debate) | Historial completo del debate multi-agente |

---

## 3. Output

`CritiqueResult` — record con tres listas:

```json
{
  "critiques": ["crítica 1", "crítica 2"],
  "gaps": ["gap 1"],
  "refinementNeeds": ["tema a re-debatir 1"]
}
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `critiques` | `List<String>` | Problemas concretos encontrados (contradicciones, vaguedades, asunciones irreales) |
| `gaps` | `List<String>` | Lo que falta completamente (flujos, roles, manejo de errores, integraciones) |
| `refinementNeeds` | `List<String>` | Temas que deben volver al debate (trade-offs no resueltos, scope ambiguo) |

---

## 4. Comportamiento

### 4.1 Flujo principal

1. Validar que `docs` y `debateHistory` no sean `null`
2. Construir `userMessage` con las 3 secciones documentales + historial de debate formateado
3. Llamar al LLM con `CRITIQUE_SYSTEM_PROMPT`
4. Parsear respuesta JSON a `CritiqueResult`
5. Retornar resultado

### 4.2 System Prompt

El LLM actúa como **auditor de producto y arquitectura**. Debe ser riguroso pero constructivo. Cada hallazgo debe ser específico y accionable. Se esperan 3-7 items por array.

### 4.3 Token Budget

Si `debateHistory` excede 30 mensajes, se trunca a:
- Primeros 5 mensajes
- Últimos 10 mensajes
- Marca de truncamiento: `... (N messages omitted) ...`

---

## 5. Manejo de errores

| Condición | Excepción | Phase | LoopCount |
|-----------|-----------|-------|-----------|
| `docs == null` | `PipelineException("docs is null", "Phase3a", 0)` | Phase3a | 0 |
| `debateHistory == null` | `PipelineException("debateHistory is null", "Phase3a", 0)` | Phase3a | 0 |
| Respuesta LLM vacía/null | `PipelineException("Empty response from LLM", "Phase3a", 0)` | Phase3a | 0 |
| JSON inválido | `PipelineException("Critique failed", "Phase3a", 0, cause)` | Phase3a | 0 |
| Error del LLM | `PipelineException("Critique failed", "Phase3a", 0, cause)` | Phase3a | 0 |

### Regla de re-lanzamiento
- `PipelineException` se relanza tal cual
- `RuntimeException` se envuelve en `PipelineException`

---

## 6. Criterios de aceptación

- [x] `CriticOrchestrator` usa `LlmClient` inyectado por constructor — sin lógica hardcodeada
- [x] System prompt definido como `private static final String`
- [x] Los 3 documentos aparecen en el prompt del LLM
- [x] El historial de debate se formatea como `[agentName] (round N): [content]`
- [x] Token budget aplicado cuando `debateHistory.size() > 30`
- [x] Campos JSON faltantes (gaps, refinementNeeds) defaultean a lista vacía
- [x] `mvn compile` exitoso
- [x] 10 tests unitarios pasan (`CriticOrchestratorTest`)

---

## 7. Tests

Ver `CriticOrchestratorTest.java` (10 tests):

| # | Test | Qué verifica |
|---|------|-------------|
| 1 | `critique_shouldReturnParsedCritiqueResult` | Parseo correcto de JSON completo |
| 2 | `critique_shouldThrowPipelineException_onInvalidJson` | JSON inválido → excepción |
| 3 | `critique_shouldThrowPipelineException_onEmptyResponse` | Respuesta vacía → excepción |
| 4 | `critique_shouldHandleMarkdownFencedResponse` | JSON entre ```json fences |
| 5 | `critique_shouldIncludeAllThreeDocumentsInPrompt` | Los 3 docs en el userMessage |
| 6 | `critique_shouldFormatDebateHistoryInUserMessage` | Formato `agentName (round N): content` |
| 7 | `critique_shouldHandleEmptyDebateHistory` | `List.of()` → "(No debate history available)" |
| 8 | `critique_shouldHandleMissingJsonFields` | Campos faltantes → listas vacías |
| 9 | `critique_shouldThrowPipelineException_onLlmFailure` | RuntimeException → PipelineException |
| 10 | `critique_shouldTruncateDebateHistoryOver30Messages` | Truncamiento a 5+10 con notice |

---

## 8. Archivos involucrados

| Archivo | Rol |
|---------|-----|
| `CriticOrchestrator.java` | Implementación |
| `CriticOrchestratorTest.java` | 10 tests unitarios |
| `CritiqueResult.java` | Record de output (no modificado) |
| `PipelineOutput.java` | Record de input (no modificado) |

---

*Fin del spec.*
