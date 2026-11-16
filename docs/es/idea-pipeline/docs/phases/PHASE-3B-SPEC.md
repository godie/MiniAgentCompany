# PHASE-3B-SPEC: Validation (Evaluación de Convergencia)

**Fecha:** 2026-05-24
**Versión:** 1.0
**Estado:** ✅ IMPLEMENTADO

---

## 1. Objetivo

Evaluar si los tres documentos sintetizados han convergido suficientemente para avanzar a definición de arquitectura (Fase 3.5), o si se necesita otro ciclo debate→síntesis. Esta fase reemplaza una fórmula dummy (`score = max(10, 100 - loopCount * 5)`) por evaluación real del LLM.

---

## 2. Inputs

| Input | Tipo | Fuente | Descripción |
|-------|------|--------|-------------|
| `docs` | `PipelineOutput` | Fase 2 (Synthesis) | Tres documentos convergidos |
| `critique` | `CritiqueResult` | Fase 3a (Critic) | Resultado de la auditoría documental |
| `loopCount` | `int` | `IdeaPipeline` | Número de iteración actual del loop de convergencia |

---

## 3. Output

`ValidationResult` — record:

```json
{
  "convergenceScore": 82,
  "shouldLoop": false,
  "reasoning": "Los documentos son coherentes entre sí. No hay gaps bloqueantes."
}
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `convergenceScore` | `int` | Puntuación 0-100 de convergencia documental |
| `shouldLoop` | `boolean` | `true` = volver a debate+síntesis, `false` = avanzar a arquitectura |
| `reasoning` | `String` | Justificación de la decisión (máx 150 palabras) |

---

## 4. Rúbrica de convergencia

| Rango | Significado | Acción |
|-------|-------------|--------|
| 0–40 | CRITICAL — Inconsistencias mayores, secciones faltantes | Loop obligatorio |
| 41–74 | NEEDS WORK — Gaps significativos pero dirección correcta | Loop recomendado |
| 75–89 | GOOD — Documentos coherentes, gaps menores aceptables | Avanzar |
| 90–100 | EXCELLENT — Documentos completos y consistentes | Avanzar |

### Influencia del loopCount
- `loopCount = 0` (primer pase): evaluación rigurosa
- `loopCount >= 2`: evaluación más permisiva (el sistema está convergiendo)

---

## 5. Comportamiento

### 5.1 Flujo principal

1. Validar que `docs` y `critique` no sean `null`
2. Construir `userMessage` con:
   - Loop count destacado
   - Los 3 documentos
   - Resultados de auditoría (critiques, gaps, refinementNeeds)
3. Llamar al LLM con `VALIDATION_SYSTEM_PROMPT`
4. Parsear respuesta JSON a `ValidationResult`
5. Validar que `convergenceScore` esté en rango 0-100
6. Retornar resultado

### 5.2 Responsabilidad del loop

El `ValidationOrchestrator` **NO** impone `maxLoops` — eso es responsabilidad de `IdeaPipeline`. El orquestador solo computa score y recomendación.

---

## 6. Manejo de errores

| Condición | Excepción | Phase | LoopCount |
|-----------|-----------|-------|-----------|
| `docs == null` | `PipelineException("docs is null", "Phase3b", loopCount)` | Phase3b | dinámico |
| `critique == null` | `PipelineException("critique is null", "Phase3b", loopCount)` | Phase3b | dinámico |
| Respuesta LLM vacía/null | `PipelineException("Empty response from LLM", "Phase3b", loopCount)` | Phase3b | dinámico |
| Score fuera de rango (0-100) | `PipelineException("Invalid convergenceScore: X (must be 0-100)", "Phase3b", loopCount)` | Phase3b | dinámico |
| JSON inválido | `PipelineException("Validation failed", "Phase3b", loopCount, cause)` | Phase3b | dinámico |
| Error del LLM | `PipelineException("Validation failed", "Phase3b", loopCount, cause)` | Phase3b | dinámico |

---

## 7. Criterios de aceptación

- [x] `ValidationOrchestrator` usa `LlmClient` inyectado por constructor
- [x] **Sin fórmula dummy** — el score viene 100% del LLM
- [x] System prompt definido como `private static final String` con rúbrica 0-100
- [x] `loopCount` incluido prominentemente en el prompt
- [x] Score validado en rango 0-100 post-parseo
- [x] Los 3 documentos + critique results aparecen en el prompt
- [x] Listas de critique vacías muestran "(none)"
- [x] `mvn compile` exitoso
- [x] 12 tests unitarios pasan (`ValidationOrchestratorTest`)

---

## 8. Tests

Ver `ValidationOrchestratorTest.java` (12 tests):

| # | Test | Qué verifica |
|---|------|-------------|
| 1 | `validate_shouldReturnParsedValidationResult` | Parseo correcto: score, shouldLoop, reasoning |
| 2 | `validate_shouldReturnShouldLoopTrue_whenScoreIsLow` | Score 35 → shouldLoop=true |
| 3 | `validate_shouldThrowPipelineException_onInvalidJson` | JSON inválido → excepción |
| 4 | `validate_shouldThrowPipelineException_onEmptyResponse` | Respuesta vacía → excepción |
| 5 | `validate_shouldThrowPipelineException_onScoreOutOfRange` | Score 150 → excepción "must be 0-100" |
| 6 | `validate_shouldIncludeLoopCountInPrompt` | `=== LOOP COUNT: N ===` en userMessage |
| 7 | `validate_shouldIncludeAllDocumentsInPrompt` | Los 3 docs en userMessage |
| 8 | `validate_shouldIncludeCritiqueResultsInPrompt` | Critiques y gaps en userMessage |
| 9 | `validate_shouldHandleMarkdownFencedResponse` | JSON entre ```json fences |
| 10 | `validate_shouldHandleEmptyCritiqueLists` | Listas vacías → "(none)" |
| 11 | `validate_shouldThrowPipelineException_onLlmFailure` | RuntimeException → PipelineException |
| 12 | `validate_shouldNotUseDummyFormula` | `verify(llmClient, times(1)).chat(...)` — confirma que el LLM fue llamado |

---

## 9. Archivos involucrados

| Archivo | Rol |
|---------|-----|
| `ValidationOrchestrator.java` | Implementación |
| `ValidationOrchestratorTest.java` | 12 tests unitarios |
| `ValidationResult.java` | Record de output (no modificado) |
| `PipelineOutput.java` | Record de input (no modificado) |
| `CritiqueResult.java` | Record de input (no modificado) |

---

*Fin del spec.*
