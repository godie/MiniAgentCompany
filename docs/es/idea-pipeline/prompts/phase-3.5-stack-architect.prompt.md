# phase-3.5-stack-architect.prompt.md v1.0

## Rol
Eres un Desarrollador Java Senior que implementa la Fase 3.5 del sistema idea-pipeline.
La Fase 3.5 recibe el IdeaContext convergido, los 3 documentos generados y el
historial de debate, y produce un `ArchitectureDoc` que define el stack
tecnológico completo, los servicios, las restricciones, el modelo de despliegue y la justificación.

## Contexto
Lee estos archivos antes de escribir cualquier código:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

---

## Lo que ya existe — no modificar

- `LlmClient.java` — interfaz, completamente implementada. 6 tests pasando.
- `LlmClientImpl.java` — @Component, completamente implementado.
- `JsonParser.java` — `public static JsonNode parse(String response)`
- `GatekeeperOrchestrator.java` — completamente implementado. 8 tests pasando.
- `DebateOrchestrator.java` — completamente implementado. 7 tests pasando.
- `SynthesisOrchestrator.java` — completamente implementado. 8 tests pasando.
- `CriticOrchestrator.java` — completamente implementado. 11 tests pasando.
- `ValidationOrchestrator.java` — completamente implementado. 11 tests pasando.
- `IdeaContext.java`, `PipelineOutput.java`, `DebateMessage.java` — records, no modificar
- `ArchitectureDoc.java` — record, no modificar:
  - `ArchitectureDoc(String stack, List<String> services, List<String> constraints, String deploymentModel, String rationale)`
- `PipelineException.java` — constructores:
  - `PipelineException(String message, String phase, int loopCount)`
  - `PipelineException(String message, String phase, int loopCount, Throwable cause)`
- `StackArchitectOrchestrator.java` — TU ARCHIVO OBJETIVO
  Actualmente devuelve valores de placeholder hardcodeados.
  Inyecta `LlmClient` por constructor.

## Estado del proyecto
- mvn clean compile → SUCCESS
- mvn test → 51/51 pasando

---

## Tu tarea

Implementa `StackArchitectOrchestrator.java` — un método: `defineStack()`

---

## System prompt — definir como constante private static final String

```java
private static final String STACK_ARCHITECT_SYSTEM_PROMPT = """
    You are a Software Architect senior who defines the technology stack
    for a software project based on converged design documents.
    You consider scalability, team complexity, time-to-market and costs.
    You justify every decision. You do not choose technology for hype.

    Analyze the idea context, the 3 documents and the debate history
    to produce a complete architecture specification.

    Respond ONLY in JSON with this exact format, no preamble, no markdown:
    {
      "stack": "Java 21, Spring Boot 3.3, PostgreSQL, Redis, Docker",
      "services": ["API Gateway", "Auth Service", "Core Service"],
      "constraints": ["Must support Java 21", "Containerized deployment"],
      "deploymentModel": "Cloud-native container orchestration (Kubernetes)",
      "rationale": "Detailed justification of all decisions..."
    }
    """;
```

---

## Método: defineStack()

**Firma:**
```java
public ArchitectureDoc defineStack(
    IdeaContext context,
    PipelineOutput documents,
    List<DebateMessage> debateHistory
)
```

**Propósito:** Enviar el contexto de la idea, los 3 documentos convergidos y el historial
de debate al LLM arquitecto y parsear la respuesta JSON estructurada en
un `ArchitectureDoc`.

**Construir el mensaje de usuario — en este orden exacto:**

```
=== IDEA CONTEXT ===
[context.enrichedSummary()]

=== CONTEXT DOCUMENT ===
[documents.contextDocument()]

=== FLOW DOCUMENT ===
[documents.flowDocument()]

=== TASK DOCUMENT ===
[documents.taskDocument()]

=== DEBATE HISTORY ===
[agentName]: content
[agentName]: content
...
```

Si `debateHistory` es null o está vacío, omite la sección `=== DEBATE HISTORY ===` por completo.

Cada mensaje de debate se formatea como: `[message.agentName()]: [message.content()]`, uno por línea.

**Pasos de implementación:**

1. Validar:
   - Si `context` es null: lanzar `PipelineException("context is null", "Phase3.5", 0)`
   - Si `context.enrichedSummary()` es null o está en blanco: lanzar `PipelineException("enrichedSummary is empty", "Phase3.5", 0)`
   - Si `documents` es null: lanzar `PipelineException("documents is null", "Phase3.5", 0)`
   - Si algún documento es null o está en blanco: lanzar `PipelineException("document is empty: [name]", "Phase3.5", 0)`
   - Si `debateHistory` es null: tratarlo como lista vacía (sin excepción — el historial es contexto opcional)

2. Construir `userMessage`:
   - Incluir siempre las secciones IDEA CONTEXT, CONTEXT DOCUMENT, FLOW DOCUMENT y TASK DOCUMENT
   - Si `debateHistory` no está vacío, añadir la sección `=== DEBATE HISTORY ===` con cada mensaje formateado como `[agentName]: content`

3. Llamar a `llmClient.chat(STACK_ARCHITECT_SYSTEM_PROMPT, userMessage)`

4. Si la respuesta es null o está en blanco:
   lanzar `PipelineException("Empty response from stack architect LLM", "Phase3.5", 0)`

5. Parsear con `JsonParser.parse(response)` — envolver RuntimeException:
   lanzar `PipelineException("Failed to parse architecture JSON", "Phase3.5", 0, e)`

6. Extraer del JsonNode:
   - `stack` → String (por defecto `""` si falta)
   - `services` → `List<String>` (por defecto `Collections.emptyList()` si falta o no es un array)
   - `constraints` → `List<String>` (por defecto `Collections.emptyList()` si falta o no es un array)
   - `deploymentModel` → String (por defecto `""` si falta)
   - `rationale` → String (por defecto `""` si falta)

7. Devolver `new ArchitectureDoc(stack, services, constraints, deploymentModel, rationale)`

**Logging:**
- `log.info()` al entrar: longitud de enrichedSummary + tamaños de los documentos + número de mensajes del historial de debate
- `log.info()` tras el parseo: valor de stack + número de servicios y de restricciones
- `log.error()` en cualquier excepción capturada antes de relanzarla

---

## Estándares de código
- Inyección solo por constructor
- @Service + @Slf4j
- Sin @Autowired en campos
- Todo el parseo JSON mediante `JsonParser.parse()`
- System prompt como constante private static final String
- Los campos JSON ausentes usan valores por defecto seguros (nunca null, nunca excepción)
- Los arrays JSON ausentes por defecto a `Collections.emptyList()`
- `debateHistory` se incluye en `userMessage` cuando no está vacío, se omite cuando es null o vacío

---

## Requisitos de pruebas (TDD)

Archivo de test: `src/test/java/com/ideapipeline/orchestrator/StackArchitectOrchestratorTest.java`
Usa `@ExtendWith(MockitoExtension.class)` y mockea `LlmClient`.

### Tests requeridos

- `defineStack_shouldReturnParsedArchitectureDoc`
  Mockea `llmClient.chat()` para que devuelva JSON válido con los 5 campos poblados.
  Verifica que todos los campos de ArchitectureDoc coinciden.

- `defineStack_shouldHandleMarkdownFencedResponse`
  Respuesta mockeada con vallas ` ```json ` alrededor de JSON válido.
  Verifica que ArchitectureDoc se parsea correctamente.

- `defineStack_shouldUseDefaultsForMissingFields`
  Respuesta mockeada: `{"stack": "Java 21"}` (sin services, constraints, deploymentModel, rationale)
  Verifica que services y constraints son listas vacías, y deploymentModel y rationale son cadenas vacías.

- `defineStack_shouldThrowPipelineException_onNullContext`
  Pasa context null.
  Verifica PipelineException con fase "Phase3.5"

- `defineStack_shouldThrowPipelineException_onEmptyEnrichedSummary`
  Pasa un IdeaContext con enrichedSummary en blanco.
  Verifica PipelineException con fase "Phase3.5"

- `defineStack_shouldThrowPipelineException_onNullDocuments`
  Pasa documents null.
  Verifica PipelineException con fase "Phase3.5"

- `defineStack_shouldThrowPipelineException_onEmptyDocument`
  Pasa un PipelineOutput con contextDocument en blanco.
  Verifica PipelineException con fase "Phase3.5"

- `defineStack_shouldThrowPipelineException_onEmptyLlmResponse`
  Mockea `llmClient.chat()` para que devuelva `""`
  Verifica PipelineException con fase "Phase3.5"

- `defineStack_shouldThrowPipelineException_onInvalidJson`
  Mockea `llmClient.chat()` para que devuelva `"not json"`
  Verifica PipelineException con fase "Phase3.5"

- `defineStack_shouldIncludeAllSectionsInUserMessage`
  Usa ArgumentCaptor para capturar userMessage.
  Verifica que contiene `"=== IDEA CONTEXT ==="`, `"=== CONTEXT DOCUMENT ==="`, `"=== FLOW DOCUMENT ==="`, `"=== TASK DOCUMENT ==="`.

- `defineStack_shouldIncludeDebateHistoryInUserMessage`
  Pasa un `debateHistory` no vacío (por ejemplo, 2 mensajes con agentName + content conocidos).
  Usa ArgumentCaptor para capturar userMessage.
  Verifica que contiene `"=== DEBATE HISTORY ==="` y cada mensaje formateado como `"[agentName]: content"`.

- `defineStack_shouldOmitDebateHistorySectionWhenHistoryIsEmpty`
  Pasa una lista `debateHistory` vacía.
  Usa ArgumentCaptor para capturar userMessage.
  Verifica que userMessage NO contiene `"=== DEBATE HISTORY ==="`.

- `defineStack_shouldOmitDebateHistorySectionWhenHistoryIsNull`
  Pasa `null` como `debateHistory`.
  Verifica que no se lanza ninguna excepción y que userMessage NO contiene `"=== DEBATE HISTORY ==="`.

---

## Criterios de aceptación
1. `mvn clean compile` → SUCCESS
2. `mvn test` → 13 tests nuevos pasan (StackArchitectOrchestratorTest)
3. `mvn test` → 64 tests en total pasando
4. `defineStack()` no tiene valores de placeholder hardcodeados
5. Los campos JSON ausentes usan valores por defecto seguros (no null, no excepción)
6. Los arrays JSON ausentes por defecto a listas vacías
7. Todo el parseo JSON mediante `JsonParser.parse()`
8. System prompt definido como constante private static final
9. `debateHistory` se incluye en `userMessage` cuando no está vacío, se omite cuando es null o vacío
10. Un `debateHistory` null no lanza — se trata como vacío
11. Un `enrichedSummary` null o en blanco lanza `PipelineException`
12. Campos de documentos null o en blanco lanzan `PipelineException`

---
