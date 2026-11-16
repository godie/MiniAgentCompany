# phase-2-synthesis.prompt.md v1.0

Implementa SynthesisOrchestrator.synthesize() — Fase 2 (Java)

Rol
Eres un Desarrollador Java Senior implementando la Fase 2 del sistema idea-pipeline.
La Fase 2 genera 3 documentos EN PARALELO usando hilos virtuales de Java 21 y StructuredTaskScope, a partir del historial de debate y el contexto de la idea.

Contexto
Leer antes de codificar: META_PIPELINE.md, CONTEXT.md, AGENTS.md

No modificar (ya implementado y probado)
- `LlmClient.java` (interfaz): chat(String systemPrompt, String userMessage); chatWithHistory(...)
- `LlmClientImpl.java` — @Component, implementado
- `JsonParser.java` — com.ideapipeline.client; public static JsonNode parse(String response)
- `GatekeeperOrchestrator.java` — implementado
- `DebateOrchestrator.java` — implementado
- `IdeaContext.java`, `DebateMessage.java`, `PipelineOutput.java` — records (no modificar)
    - PipelineOutput(String contextDocument, String flowDocument, String taskDocument)
- `PipelineException.java` — constructores disponibles
- Archivo objetivo: `SynthesisOrchestrator.java`— actualmente lanza UnsupportedOperationException
    - Inyectar LlmClient por constructor: public SynthesisOrchestrator(LlmClient llmClient)

Estado del proyecto
- mvn clean compile → SUCCESS
- mvn test → 21/21 pasando

Tu tarea
Implementa SynthesisOrchestrator.java — un método: synthesize()

System prompts — definir como constantes private static final String
```java
private static final String CONTEXT_DOC_PROMPT = """
You are a technical writer. Based on the idea context and debate history,
generate a context document in Markdown with these sections:
## Vision
## Problem
## Target Users
## Scope
## Constraints
## Assumptions
## Glossary
Be precise and concise. Only include what was discussed.
Respond in Markdown only, no preamble.
""";

private static final String FLOW_DOC_PROMPT = """
You are a business analyst. Based on the idea context and debate history,
generate a flow document in Markdown with these sections:
## Main User Flows (happy path)
## Alternative Flows
## Edge Cases
## Mermaid Diagrams (at least one)
Be precise and concise. Only include what was discussed.
Respond in Markdown only, no preamble.
""";

private static final String TASK_DOC_PROMPT = """
You are a tech lead. Based on the idea context and debate history,
generate a task document in Markdown with this structure:
## Epic 1: [name]
### User Story: [story]
#### Task: [task title]
- Description: ...
- Acceptance criteria: ...
- Estimate: S | M | L
Prioritize by value and dependencies.
Respond in Markdown only, no preamble.
""";
```
Método: synthesize()
Firma:
```java
public PipelineOutput synthesize(
IdeaContext context,
List<DebateMessage> debateHistory
) throws Exception{}
```
Propósito: Generar 3 documentos EN PARALELO — context.md, flows.md, tasks.md — cada uno mediante una llamada LLM separada que se ejecuta en un hilo virtual. Los 3 reciben la misma entrada: contexto de la idea + historial de debate completo.

Mensaje de usuario (compartido por los 3 sintetizadores)
Construye el userMessage compartido en este orden exacto:

Idea summary: [context.enrichedSummary()]
```
Debate history:
[agentName]: [content]
[agentName]: [content]
...
```
Si debateHistory está vacío, userMessage debe ser:

Idea summary: [context.enrichedSummary()]

No debate history available.

Pasos de implementación
1. Validar entradas:
    - Si context == null: `throw PipelineException("context is null", "Phase2", 0)`
    - Si debateHistory == null: `throw PipelineException("debateHistory is null", "Phase2", 0)`
    - Si debateHistory está vacío: NO lanzar; log.warn("No debate history — synthesizing from context only") y continuar

2. Construir el mensaje de usuario compartido:
    - Formatear cada DebateMessage como "[agentName]: content"
    - Unir con salto de línea en el orden proporcionado
    - Anteponer "Idea summary: [context.enrichedSummary()]\n\n" y "Debate history:" (o "No debate history available." si está vacío)

3. Lanzar 3 tareas en paralelo usando StructuredTaskScope.ShutdownOnFailure:
    - Tarea 1: `llmClient.chat(CONTEXT_DOC_PROMPT, userMessage)` → contextDocument
    - Tarea 2: `llmClient.chat(FLOW_DOC_PROMPT, userMessage)` → flowDocument
    - Tarea 3: `llmClient.chat(TASK_DOC_PROMPT, userMessage)` → taskDocument

4. Llamar a scope.join().throwIfFailed()

5. Validar cada resultado — si es nulo o está en blanco:
   lanzar PipelineException("Empty document from synthesizer: [doc name]", "Phase2", 0)

6. Devolver new PipelineOutput(contextDocument, flowDocument, taskDocument)

Logging
- `log.info()` al entrar al método: tamaño de debateHistory (si debateHistory != null)
- `log.info()` tras completar: confirmar que se generaron 3 documentos
- `log.error()` en cualquier excepción capturada antes de relanzarla

Manejo de excepciones
- `InterruptedException`: `Thread.currentThread().interrupt(); throw PipelineException("Synthesis interrupted", "Phase2", 0, e)`
- Cualquier otra excepción: lanzar `PipelineException("Synthesis failed", "Phase2", 0, e)`

Estándares de código
- @Service + @Slf4j en la clase
- Solo inyección por constructor; sin @Autowired en campos
- Usar `StructuredTaskScope.ShutdownOnFailure` — no CompletableFuture
- Los 3 system prompts como constantes private static final String
- Sin cadenas hardcodeadas fuera de las constantes
- Las respuestas de documento nulas/en blanco lanzan PipelineException

Requisitos de pruebas (TDD)
Archivo de pruebas: `src/test/java/com/ideapipeline/orchestrator/SynthesisOrchestratorTest.java`
Usa @ExtendWith(MockitoExtension.class) y mockea LlmClient.

Pruebas requeridas
- `synthesize_shouldReturnPipelineOutputWithAllThreeDocuments`
  Mockea llmClient.chat() para devolver "Context doc", "Flow doc", "Task doc" respectivamente.
  Verifica que los campos de PipelineOutput coinciden.

- `synthesize_shouldCallLlmThreeTimes`
  Verifica que se llama a llmClient.chat() exactamente 3 veces.

- `synthesize_shouldThrowPipelineException_onNullContext`
  Llamar a synthesize(null, history) → esperar PipelineException con fase "Phase2"

- `synthesize_shouldSucceedWithEmptyHistory`
  Llamar a synthesize(context, List.of()) → esperar éxito
  Verifica que userMessage contiene "No debate history available"

- `synthesize_shouldThrowPipelineException_onEmptyDocumentResponse`
  Mockea llmClient.chat() para devolver "" en todas las llamadas → esperar PipelineException con fase "Phase2"

- `synthesize_shouldFormatDebateHistoryInUserMessage`
  Usa ArgumentCaptor<String> para capturar userMessage; verifica que contiene "[agentName]: content" para cada DebateMessage

- `synthesize_shouldIncludeEnrichedSummaryInUserMessage`
  Verifica que userMessage contiene context.enrichedSummary()

- `synthesize_shouldThrowPipelineException_onLlmFailure`
  Mockea llmClient.chat() para lanzar RuntimeException("LLM error") → esperar PipelineException con fase "Phase2"

Criterios de aceptación
1. `mvn clean compile` → SUCCESS
2. `mvn test` → 8 pruebas nuevas para SynthesisOrchestratorTest pasan
3. `mvn test` → 29 pruebas en total pasando
4. `synthesize()` no tiene `UnsupportedOperationException`
5. El paralelismo usa `StructuredTaskScope.ShutdownOnFailure`
6. Las 3 llamadas al LLM se ejecutan en paralelo — no en secuencia
7. `InterruptedException` re-interrumpe el hilo
8. Las respuestas de documento nulas/en blanco lanzan `PipelineException`
9. Todos los system prompts definidos como constantes private static final

Notas adicionales (comentario en el código)
// NOTA: si debateHistory supera los 50 mensajes, considera resumirlo
// antes de enviarlo al LLM para evitar problemas de límite de tokens.
// Esto es una mejora futura conocida — no requerida para el MVP.
