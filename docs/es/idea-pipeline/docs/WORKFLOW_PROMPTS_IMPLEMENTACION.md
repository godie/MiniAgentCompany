# Workflow sistemático: prompt → implementación → test → documentación

**Fecha:** 2026-05-25
**Versión:** 1.2

## Objetivo
Definir una forma repetible de avanzar fase por fase sin perder consistencia entre código, prompts y documentación.

---

## 1. Estructura documental recomendada

```
idea-pipeline/
├── prompts/
│   ├── phase-0-gatekeeper.prompt.md          ✅ Existe
│   ├── phase-1-debate.prompt.md              ✅ Existe
│   ├── phase-2-synthesis.prompt.md           ✅ Existe
│   ├── llm-client.prompt.md                  ✅ Existe
│   ├── engineer-lead.prompt.md               ✅ Existe
│   ├── phase-3a-critique.prompt.md           ✅ Creado
│   ├── phase-3b-validation.prompt.md         ✅ Creado
│   ├── phase-3.5-stack-architecture.prompt.md ✅ Creado
│   └── phase-4-planning-poker.prompt.md      ✅ Creado
└── docs/
    ├── REVISION_PROYECTO.md                  ✅ Creado
    ├── PLAN_SIGUIENTES_PASOS.md              ✅ Creado
    ├── PLAN_FASES_3A_4.md                    ✅ Creado
    ├── WORKFLOW_PROMPTS_IMPLEMENTACION.md    ✅ Creado
    ├── phases/
    │   ├── PHASE-3A-SPEC.md                  ✅ Creado (2026-05-25)
    │   ├── PHASE-3B-SPEC.md                  ✅ Creado (2026-05-25)
    │   ├── PHASE-3.5-SPEC.md                 ✅ Creado (2026-05-25)
    │   └── PHASE-4-SPEC.md                   ✅ Creado (2026-05-25)
    ├── reports/
    │   ├── PHASE-3A-IMPLEMENTATION-REPORT.md ✅ Creado (2026-05-25)
    │   ├── PHASE-3B-IMPLEMENTATION-REPORT.md ✅ Creado (2026-05-25)
    │   ├── PHASE-3.5-IMPLEMENTATION-REPORT.md ✅ Creado (2026-05-25)
    │   └── PHASE-4-IMPLEMENTATION-REPORT.md  ✅ Creado (2026-05-25)
    └── decisions/
        ├── ADR-001-llm-provider.md           ✅ Creado (2026-05-24)
        └── ADR-002-tasks-markdown-format.md  ✅ Creado (2026-05-24)
```

---

## 2. Secuencia recomendada por fase

Para cada fase pendiente repetir siempre este ciclo:

### 2.1 Spec
- Definir objetivo de la fase.
- Listar inputs esperados.
- Especificar outputs (formato exacto).
- Enumerar errores esperados.
- Fijar criterios de aceptación.

### 2.2 Prompt
- Escribir el prompt de implementación en `.md`.
- Listar archivos que ya existen y no deben modificarse.
- Indicar archivos a modificar.
- Definir system prompts necesarios.
- Fijar tests requeridos.
- Fijar comandos de validación (`mvn clean compile`, `mvn test`).

### 2.3 Implementación
- Modificar solo los archivos de la fase.
- No tocar contratos estables (records, enums) salvo que haya decisión documentada.
- Seguir estándares del proyecto: `@Service`, `@Slf4j`, constructor injection, `StructuredTaskScope`.
- Manejar errores con `PipelineException`.

### 2.4 Testing
- Unit tests primero (Mockito, `@ExtendWith(MockitoExtension.class)`).
- Luego tests de integración si aplica.
- Registrar resultados.
- Verificar que `mvn test` pasa.

### 2.5 Documentación de cierre
- Escribir reporte de implementación en `docs/reports/`.
- Registrar decisiones tomadas en `docs/decisions/` si aplica.
- Anotar follow-ups si quedan pendientes.

---

## 3. Plantilla mínima para futuros prompts

Cada archivo `phase-X-*.prompt.md` debería incluir estas secciones:

```markdown
# phase-X-name.prompt.md

## Role
Qué rol adopta el agente implementador.

## Context
Qué archivos debe leer antes de tocar código.

## What already exists
Qué piezas no debe modificar (listarlas explícitamente).

## Your task
Qué clase o clases debe implementar.

## Required behavior
Reglas exactas del método o flujo. Incluir system prompts como constantes.

## Error handling
Cómo envolver excepciones y qué `PipelineException` usar.

## Testing requirements
Tests obligatorios con nombres de métodos y casos a cubrir.

## Acceptance criteria
Checklist verificable (compila, tests pasan, sin UnsupportedOperationException, etc.).

## Files to modify
Lista cerrada de archivos permitidos.

## Implementation hints
Patrones de código, snippets de referencia, convenciones.
```

---

## 4. Reglas para mantener consistencia

### 4.1 Los records son la fuente de verdad
Si un prompt contradice un record Java, gana el record Java o se documenta un cambio explícito antes de implementar.

### 4.2 Los outputs del LLM deben ser fáciles de parsear
Siempre que el resultado vaya a mapearse a código:
- **Preferir JSON** para intercambio máquina-a-máquina.
- **Usar Markdown** cuando el output sea documental para humanos.
- Si un markdown debe ser parseado luego, darle una gramática estricta.

### 4.3 Cada fase debe dejar evidencia en `.md`
Al completar una fase, generar al menos:
- Un spec (`docs/phases/PHASE-X-SPEC.md`).
- Un implementation report (`docs/reports/PHASE-X-IMPLEMENTATION-REPORT.md`).
- Una nota de decisiones si hubo cambios de contrato (`docs/decisions/ADR-XXX.md`).

### 4.4 El pipeline no debe depender de placeholders silenciosos
Si una fase sigue siendo mock o dummy, debe quedar explícitamente documentado.

### 4.5 Convenciones de código
- Constructor injection only — nunca `@Autowired` en campos.
- `@Service` + `@Slf4j` en todos los orchestrators.
- `StructuredTaskScope.ShutdownOnFailure` para paralelismo (no `CompletableFuture`).
- `@Component` para implementaciones de client.
- Excepciones siempre envueltas en `PipelineException`.

---

## 5. Checklist de calidad por fase

Antes de dar una fase por terminada, verificar:

- [ ] El prompt existe en `prompts/`.
- [ ] La spec existe en `docs/phases/`.
- [ ] La implementación elimina placeholders de esa fase.
- [ ] Hay tests unitarios nuevos o actualizados.
- [ ] `mvn clean compile` pasa sin errores.
- [ ] `mvn test` pasa (todos los tests, no solo los nuevos).
- [ ] Hay reporte de implementación en `docs/reports/`.
- [ ] No quedaron contratos ambiguos sin documentar.
- [ ] No hay `UnsupportedOperationException` en los métodos implementados.
- [ ] Los logs están en niveles adecuados (`info` para entrada/salida, `warn` para anomalías, `error` para fallos).

---

## 6. Workflow paso a paso para las siguientes fases

| Paso | Acción | Output |
|------|--------|--------|
| ✅ 1 | Crear spec Phase 3a | `docs/phases/PHASE-3A-SPEC.md` |
| ✅ 2 | Crear prompt Phase 3a | `prompts/phase-3a-critique.prompt.md` |
| ✅ 3 | Implementar + testear Phase 3a | `CriticOrchestrator.java` + tests |
| ✅ 4 | Crear spec Phase 3b | `docs/phases/PHASE-3B-SPEC.md` |
| ✅ 5 | Crear prompt Phase 3b | `prompts/phase-3b-validation.prompt.md` |
| ✅ 6 | Implementar + testear Phase 3b | `ValidationOrchestrator.java` + tests |
| ✅ 7 | Crear spec Phase 3.5 | `docs/phases/PHASE-3.5-SPEC.md` |
| ✅ 8 | Crear prompt Phase 3.5 | `prompts/phase-3.5-stack-architecture.prompt.md` |
| ✅ 9 | Implementar + testear Phase 3.5 | `StackArchitectOrchestrator.java` + tests |
| ✅ 10 | Crear ADR del formato tasks.md | `docs/decisions/ADR-002-tasks-markdown-format.md` |
| ✅ 11 | Crear spec Phase 4 | `docs/phases/PHASE-4-SPEC.md` |
| ✅ 12 | Crear prompt Phase 4 | `prompts/phase-4-planning-poker.prompt.md` |
| ✅ 13 | Implementar parser + orchestrator Phase 4 | `PlanningPokerOrchestrator.java` + tests |
| ✅ 14 | Corregir API (`RunRequest`, `PipelineController`) | `RunRequest.java`, `PipelineController.java` |
| ✅ 15 | Test de integración end-to-end | Test de integración |

---

## 7. Recomendación final

La combinación más segura para este proyecto es:

- **Markdown** para documentación humana (prompts, specs, reports, docs).
- **JSON** para respuestas estructuradas del LLM (fácil de parsear con `JsonParser`).
- **Markdown con metadatos HTML** (`<!-- META: {...} -->`) cuando un documento humano deba ser parseado después (como `tasks.md` en Fase 4).

Este enfoque mantiene trazabilidad, automatización y buena legibilidad sin mezclar responsabilidades.
