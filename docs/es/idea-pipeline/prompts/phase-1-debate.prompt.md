# Fase 1: Prompt de debate

## Objetivo
Crear un marco de debate estructurado para evaluar ideas mediante cuestionamiento adversarial y crítica constructiva.

---

# phase-1-debate.prompt.md v1.1

## Rol
Eres un Desarrollador Java Senior implementando la Fase 1 del sistema idea-pipeline.
La Fase 1 ejecuta un debate multiagente en el que 3 agentes discuten una idea en paralelo
usando hilos virtuales de Java 21 y StructuredTaskScope.

## Contexto
Lee estos archivos antes de escribir cualquier código:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

---

## Lo que ya existe — no modificar

Estos archivos están COMPLETAMENTE IMPLEMENTADOS y probados:

- LlmClient.java — interfaz:
 - chat(String systemPrompt, String userMessage)
 - chatWithHistory(String systemPrompt, List<DebateMessage> history, String userMessage)

- LlmClientImpl.java — @Component, completamente implementado. 6 tests pasando.

- JsonParser.java — paquete com.ideapipeline.client
 public static JsonNode parse(String response)

- GatekeeperOrchestrator.java — completamente implementado. 8 tests pasando.

- IdeaContext.java, DebateMessage.java — records, no modificar

- PipelineException.java — constructores:
 - PipelineException(String message, String phase, int loopCount)
 - PipelineException(String message, String phase, int loopCount, Throwable cause)

- DebateOrchestrator.java — TU ARCHIVO OBJETIVO
 Actualmente lanza UnsupportedOperationException.
 Inyecta LlmClient por constructor.

---

## Estado del proyecto
- mvn clean compile → SUCCESS
- mvn test → 14/14 pasando

---

## Tu tarea

Implementa DebateOrchestrator.java — un método: runDebate()

---

## Los 3 agentes — definir como records private static final

```java
private record DebateAgent(String name, String systemPrompt) {}

private static final List<DebateAgent> AGENTS = List.of(
 new DebateAgent("ProductAgent", """
 You are a senior Product Manager focused on user value, business model,
 and market fit. Ask about the 'for whom' and the 'why'.
 Reference other agents by name. Max 3 sentences per turn.
 Be concrete and specific.
 """),
 new DebateAgent("ArchitectAgent", """
 You are a Software Architect focused on technical feasibility, stack,
 data model, integrations and scalability.
 Question unnecessary complexity.
 Reference other agents by name. Max 3 sentences per turn.
 Be concrete and specific.
 """),
 new DebateAgent("CriticAgent", """
 You are a devil's advocate. Find risks, unvalidated assumptions,
 legal issues, adoption problems and monetization gaps.
 You are not negative — you are rigorous.
 Reference other agents by name. Max 3 sentences per turn.
 Be concrete and specific.
 """)
);
```

---

Plantilla del prompt de turno (CONSTANTE OBLIGATORIA)

```java
private static final String TURN_PROMPT_TEMPLATE = """
The topic is: "%s"
This is round %d of the debate.
Respond to the most recent points as %s.
Be concise — max 3 sentences.
""";
```

---

## Implementación: DebateOrchestrator.java

### Anotaciones de clase

```java
@Service
@Slf4j
public class DebateOrchestrator {
    
    private final LlmClient llmClient;
    
    public DebateOrchestrator(LlmClient llmClient) {
        this.llmClient = llmClient;
    }
    
    // ... resto de la implementación
}
```

---

## Método: runDebate()

Firma:

```java
public List<DebateMessage> runDebate(IdeaContext context, int rounds) throws Exception
```

---

## Propósito

Ejecutar N rondas de debate.

Dentro de cada ronda:
- Los 3 agentes se ejecutan EN PARALELO
- Cada agente recibe una INSTANTÁNEA del historial ANTES de la ronda
- Los agentes de la misma ronda NO ven las respuestas de los demás

---

## Implementación completa

```java
public List<DebateMessage> runDebate(IdeaContext context, int rounds) throws Exception {
    // 1. Validar la entrada
    if (rounds <= 0) {
        throw new PipelineException("rounds must be > 0", "Phase1", 0);
    }

    // 2. Inicializar el historial
    List<DebateMessage> history = new ArrayList<>();
    history.add(new DebateMessage("System", context.enrichedSummary(), 0));

    // 3. Ejecutar las rondas
    for (int round = 1; round <= rounds; round++) {
        
        // a. Registrar el inicio
        log.info("Starting round {} of {}", round, rounds);

        // b. Instantánea - COPIA INMUTABLE
        List<DebateMessage> snapshot = List.copyOf(history);

        // c. Ejecución en paralelo con StructuredTaskScope.ShutdownOnFailure
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<StructuredTaskScope.Subtask<DebateMessage>> subtasks = AGENTS.stream()
                .map(agent -> scope.fork(() -> {
                    
                    String response = llmClient.chatWithHistory(
                        agent.systemPrompt(),
                        snapshot,
                        TURN_PROMPT_TEMPLATE.formatted(
                            context.rawIdea(),
                            round,
                            agent.name()
                        )
                    );

                    if (response == null || response.isBlank()) {
                        log.warn("Empty response from {}", agent.name());
                        response = "[No response]";
                    }

                    return new DebateMessage(agent.name(), response, round);
                }));

            List<DebateMessage> messages = scope.join().throwIfFailed();

            // Añadir todas las respuestas al historial
            history.addAll(messages);

            log.info("Completed round {}, received {} responses", round, messages.size());
        }

        // 4. El manejo de excepciones lo gestiona ShutdownOnFailure automáticamente
        // pero el manejo explícito de InterruptedException:
        // (incluido en el bloque try de arriba)
    }

    // 5. Devolver el resultado
    return history;
}
```

---

## Estándares de código

- Solo inyección por constructor (LlmClient)
- Anotar la clase con @Service y @Slf4j
- Sin inyección de campos
- Usar StructuredTaskScope.ShutdownOnFailure
- La instantánea DEBE ser List.copyOf()
- Sin cadenas hardcodeadas fuera de las constantes
- Manejar de forma defensiva las respuestas nulas/en blanco del LLM
- Envolver siempre los errores en PipelineException

---

## Requisitos de pruebas (TDD)

Archivo de pruebas: `src/test/java/com/ideapipeline/orchestrator/DebateOrchestratorTest.java`

Usa:

```java
@ExtendWith(MockitoExtension.class)
@Mock LlmClient llmClient;
```

---

### Pruebas requeridas

1. `runDebate_shouldReturnHistoryWithSeedAndAllRounds`
2. `runDebate_shouldSetCorrectRoundNumberOnMessages`
3. `runDebate_shouldSetCorrectAgentNames`
4. `runDebate_shouldThrowPipelineException_onInvalidRounds`
5. `runDebate_shouldThrowPipelineException_onLlmFailure`
6. `runDebate_shouldIncludeSeedMessageInHistory`
7. `runDebate_shouldHandleSingleRound`

---

### Prueba ADICIONAL OBLIGATORIA (corrección de la instantánea)

**`runDebate_shouldPassImmutableSnapshotToAgents`**

Usa `ArgumentCaptor<List<DebateMessage>>`

Verifica:
- Las listas pasadas a `chatWithHistory()` NO son la misma instancia que history
- Las listas no se mutan entre rondas

---

### Prueba ADICIONAL OBLIGATORIA (manejo de respuesta vacía)

**`runDebate_shouldHandleEmptyResponse`**

Mock:
```java
when(llmClient.chatWithHistory(...)).thenReturn("");
```

Verifica:
- El contenido del mensaje es "[No response]"

---

## Criterios de aceptación

1. `mvn clean compile → SUCCESS`
2. `mvn test → +9 pruebas pasan (DebateOrchestratorTest)`
3. Total de pruebas ≥ 23 pasando
4. Ninguna UnsupportedOperationException
5. El paralelismo usa StructuredTaskScope.ShutdownOnFailure
6. La instantánea usa List.copyOf()
7. InterruptedException preserva el flag de interrupción
8. TURN_PROMPT_TEMPLATE definido como constante
9. Respuestas vacías/nulas manejadas de forma segura
10. Logs presentes en cada ronda

---

Fin de phase-1-debate.prompt.md v1.1
