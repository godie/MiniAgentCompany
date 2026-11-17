# Revisión del proyecto IdeaPipeline

**Fecha:** 2026-05-25
**Versión:** 1.1

## 1. Resumen ejecutivo

El proyecto tiene una base funcional sólida para todas las fases del pipeline:

- **Fase 0 (Gatekeeper)** implementada con llamadas reales al LLM.
- **Fase 1 (Debate)** implementada con paralelismo usando `StructuredTaskScope`.
- **Fase 2 (Synthesis)** implementada con generación paralela de documentos.
- **LlmClient / LlmClientImpl** implementados.
- **IdeaPipeline** implementado con loop de convergencia.
- **PipelineController** expuesto vía REST.

**El pipeline está completo de punta a punta.** Las 7 fases ejecutan lógica real con LLM, la API expone el pipeline con datos reales del request, y hay 87 tests unitarios + controller + integración pasando. Los desajustes entre documentación, configuración y contratos de API han sido resueltos.

---

## 2. Fortalezas actuales

### 2.1 Base técnica correcta
- Uso de **Java 21**.
- Paralelismo moderno con **virtual threads** y **StructuredTaskScope**.
- Separación razonable entre `client`, `orchestrator`, `pipeline`, `controller`, `model`.
- Contratos de datos definidos con **records**.

### 2.2 Diseño del pipeline bien documentado
Los archivos `idea.md`, `META_PIPELINE.md` y `AGENTS.md` dejan claro:
- el propósito del sistema,
- las fases,
- los outputs esperados,
- la lógica de convergencia.

### 2.3 Buen punto de partida para TDD
Ya existen tests para:
- `LlmClientImpl`
- `GatekeeperOrchestrator`
- `DebateOrchestrator`
- `SynthesisOrchestrator`

Esto permite extender el proyecto con una estrategia incremental y verificable.

---

## 3. Gaps y riesgos detectados

### 3.1 Fases finales — estado actualizado (2026-05-24)

| Fase | Clase | Estado actual | Tests |
|------|-------|---------------|-------|
| 3a | `CriticOrchestrator` | ✅ Implementado — llama al LLM, constructor injection, token budget | 10 |
| 3b | `ValidationOrchestrator` | ✅ Implementado — rúbrica LLM, sin fórmula dummy, validación de rango | 12 |
| 3.5 | `StackArchitectOrchestrator` | ✅ Implementado — arquitecto senior LLM, validación de campos requeridos | 11 |
| 4 | `PlanningPokerOrchestrator` | ✅ Implementado — 2 fases LLM (parseo + planning poker), cross-validation de totalPoints | 14 |

**Avance:** Las 4 fases finales implementadas (47 tests nuevos). MVP completo.

**Impacto:**
- El pipeline produce convergencia real basada en LLM.
- La arquitectura resultante está ligada al contexto generado.
- El planning poker transforma `tasks.md` endurecido en un `TaskGraph` con estimaciones, dependencias y votos por miembro del equipo.

### 3.2 Desajustes entre documentación y código ✅ RESUELTO (2026-05-24)

| Documento decía | Código/Config tenía | Acción |
|----------------|---------------------|--------|
| OpenAI / gpt-4o-mini como provider | Ollama como provider (qwen3.5:4b) | Docs actualizados a Ollama/qwen3.5:4b |
| `CONTEXT.md` como archivo canónico | El archivo real se llamaba `CONTEX.md` | Renombrado a `CONTEXT.md` |
| Spring Boot 3.3 en CONTEX.md | Spring Boot 4.1.0-SNAPSHOT en pom.xml | Actualizado a 4.1.0-SNAPSHOT |
| Spring AI 0.8.1 en docs | Spring AI 2.0.0-SNAPSHOT en pom.xml | Actualizado a 2.0.0-SNAPSHOT |

**Impacto (resuelto):**
- Documentación y código ahora alineados.
- ADR-001 documenta la decisión del proveedor LLM.

### 3.3 Gap funcional en la API — RESUELTO (2026-05-25)
`PipelineController` corregido y con cobertura de tests:

- ✅ `runPipeline()` usa `request.answers()` real (con fallback seguro).
- ✅ `runPipeline()` usa `request.team()` real (con fallback a default).
- ✅ `RunRequest` incluye campo `List<RefinementQA> answers`.
- ✅ Tests de controller: `PipelineControllerTest` (8 tests).
- ✅ Test de integración: `IdeaPipelineIntegrationTest` (3 tests — pipeline completo, loop de convergencia, startPipeline).

### 3.4 Riesgo de parseo para Fase 4 ✅ RESUELTO (2026-05-24)
La Fase 4 necesita transformar `tasks.md` en `List<Task>`.

**Solución aplicada:**
- Formato endurecido con IDs secuenciales (`EPIC-1`, `US-1`, `TASK-1`) y bloques `<!-- META: {...} -->`.
- ADR-002 documenta el formato canónico.
- `PlanningPokerOrchestrator.parseTasksFromMarkdown()` parsea vía LLM con system prompt dedicado.

### 3.5 Cobertura de testing — actualizada (2026-05-25)

| Nivel | Estado |
|-------|--------|
| Tests unitarios — fases 0, 1, 2 | ✅ 23 tests |
| Tests unitarios — fases 3a, 3b, 3.5, 4 | ✅ 47 tests |
| Tests unitarios — `LlmClientImpl` | ✅ 6 tests |
| Tests de controller — `PipelineControllerTest` | ✅ 8 tests |
| Tests de integración — `IdeaPipelineIntegrationTest` | ✅ 3 tests |
| **Total** | **87 tests** |

### 3.6 Dependencia de snapshots
El proyecto usa versiones snapshot de Spring Boot y Spring AI.

**Impacto:** Puede introducir inestabilidad o cambios de API. Conviene vigilar compatibilidad mientras se completa el MVP.

---

## 4. Conclusión de la revisión

### Estado general
El proyecto ha alcanzado el **MVP funcional** — las 7 fases del pipeline ejecutan lógica real con LLM.

### Prioridad real
La prioridad ahora es:
1. ~~Completar las fases 3a, 3b, 3.5 y 4.~~ ✅ Completado
2. ~~Alinear documentación + configuración + contratos.~~ ✅ Completado
3. ~~Cerrar el flujo end-to-end desde API hasta outputs finales.~~ ✅ Completado (API + controller tests + integración)
4. ~~Agregar tests de integración y controller.~~ ✅ Completado (11 tests nuevos)
5. Revisión final de documentación.

### Recomendación principal
El siguiente tramo debe ejecutarse en este orden:

1. ~~**Alinear base documental y contratos.**~~ ✅
2. ~~**Implementar 3a y 3b** para tener convergencia real.~~ ✅
3. ~~**Implementar 3.5** para generar arquitectura real.~~ ✅
4. ~~**Endurecer el formato de `tasks.md`** para parseo seguro.~~ ✅
5. ~~**Implementar Fase 4.**~~ ✅
6. ~~**Cerrar la API y tests end-to-end.**~~ ✅7. **Revisión final de documentación.** 🔄
