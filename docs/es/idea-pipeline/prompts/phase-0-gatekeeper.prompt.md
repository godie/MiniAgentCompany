# phase-0-gatekeeper.prompt.md v1.2

## Rol
Eres un Desarrollador Java Senior implementando la Fase 0 del sistema idea-pipeline.
La Fase 0 es el punto de entrada del pipeline — recibe una idea cruda y produce
un contexto enriquecido que usarán todas las fases posteriores.

## Contexto
Lee estos archivos antes de escribir cualquier código:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

## Lo que ya existe — no modificar

Estos archivos están COMPLETAMENTE IMPLEMENTADOS y probados:

- LlmClient.java — interfaz con dos métodos:
 - chat(String systemPrompt, String userMessage)
 - chatWithHistory(String systemPrompt, List<DebateMessage> history, String userMessage)

- LlmClientImpl.java — @Component, completamente implementado con Spring AI ChatClient.
 Constructor: public LlmClientImpl(ChatClient chatClient)
 Lanza PipelineException en caso de fallo. 6 tests pasando.

- JsonParser.java — paquete com.ideapipeline.client. Utilidad estática.
 Método: public static JsonNode parse(String response)
 Elimina
 Lanza RuntimeException si el JSON no es válido.

- `PipelineConfig.java` — tiene @Bean para ChatClient y ExecutorService virtualThreadExecutor()
 
java
 @Bean
 public ChatClient chatClient(ChatClient.Builder builder) {
 return builder.build();
 }
 

- `RawIdea.java`, `RefinementQA.java`, `IdeaContext.java` — records, no modificar

- `PipelineException.java` — firma del constructor:
 `PipelineException(String message, String phase, int loopCount)`
 `PipelineException(String message, String phase, int loopCount, Throwable cause)`

- `GatekeeperOrchestrator.java` — TU ARCHIVO OBJETIVO
 Actualmente lanza UnsupportedOperationException en todos los métodos.
 Inyecta LlmClient (la interfaz) por constructor:
 `public GatekeeperOrchestrator(LlmClient llmClient)`

## Estado del proyecto
- mvn clean compile → SUCCESS
- mvn spring-boot:run → STARTS
- GET /pipeline/health → 200 OK
- LlmClientImplTest → 6/6 pasando

---

## Tu tarea

Implementa `GatekeeperOrchestrator.java` — dos métodos.

---

### Método 1: generateRefinementQuestions()

**Propósito:** Recibir una idea cruda y devolver 5-7 preguntas enfocadas que
aclaren las incógnitas más críticas antes de que empiece el debate.

**Constante del system prompt:**
java
private static final String QUESTIONS_SYSTEM_PROMPT = """
 You are a product strategist expert at turning vague ideas into clear specs.
 Your job is to ask the MINIMUM and MOST IMPORTANT questions to understand an idea.
 Maximum 6 questions. Each question must disambiguate something critical.
 Respond ONLY in JSON with this exact format, no preamble, no markdown:
 {"questions": ["question 1", "question 2", ...]}
 """;

**Pasos de implementación:**
1. Si `idea.description()` es nulo o está en blanco: lanzar `PipelineException("Empty idea description", "Phase0", 0)`
2. Llamar a `llmClient.chat(QUESTIONS_SYSTEM_PROMPT, idea.description())`
3. Si la respuesta es nula o está en blanco: lanzar `PipelineException("Empty response from LLM", "Phase0", 0)`
4. Parsear la respuesta con `JsonParser.parse(response)` — envolver RuntimeException:
 `throw new PipelineException("Failed to parse questions JSON", "Phase0", 0, e)`
5. Extraer el array `questions` del JsonNode como `List<String>`
6. Devolver la lista

**Logging:**
- `log.info()` al entrar al método: longitud de la descripción de la idea
- `log.info()` tras el parseo: número de preguntas generadas
- `log.error()` en cualquier excepción capturada antes de relanzarla

---

### Método 2: buildContext()

**Propósito:** Recibir la idea cruda + las respuestas del usuario y producir un
IdeaContext enriquecido que capture la esencia, el problema, el usuario objetivo y las restricciones.

**Constante del system prompt:**
java
private static final String CONTEXT_SYSTEM_PROMPT = """
You are a product strategist. Given an idea and refinement answers,
 generate a concise executive summary (max 200 words) that captures:
 - The core essence of the idea
 - The problem it solves
 - The target user
 - Key constraints and assumptions
 Respond with plain text only, no JSON, no markdown.
 """;

**Pasos de implementación:**

1. Formatear las respuestas como:
 
 Q: [question]
 A: [answer]
 
 unidas por doble salto de línea
2. Construir userMessage:

 Original idea: [idea.description()]

 Refinement answers:
 [formatted QA block]

3. Llamar a `llmClient.chat(CONTEXT_SYSTEM_PROMPT, userMessage)`
4. Si la respuesta es nula o está en blanco: lanzar `PipelineException("Empty response from LLM", "Phase0", 0)`
5. Devolver `new IdeaContext(idea.description(), answers, response.trim())`

**Logging:**
- `log.info()` al entrar al método: longitud de la descripción de la idea y número de respuestas
- `log.debug()` antes de la llamada al LLM: userMessage formateado
- `log.error()` en cualquier excepción capturada antes de relanzarla

---

## Estándares de código
- Solo inyección por constructor — `public GatekeeperOrchestrator(LlmClient llmClient)`
- @Service + @Slf4j
- Sin @Autowired en campos
- Todo el parseo JSON a través de `JsonParser.parse()`
- Todas las excepciones del LLM envueltas en `PipelineException`
- System prompts definidos como constantes private static final String
- Sin cadenas hardcodeadas fuera de las constantes de system prompt

---

## Requisitos de pruebas (TDD)

Archivo de pruebas: `src/test/java/com/ideapipeline/orchestrator/GatekeeperOrchestratorTest.java`
Usa `@ExtendWith(MockitoExtension.class)` y mockea `LlmClient`.

### Pruebas de generateRefinementQuestions

- `generateRefinementQuestions_shouldReturnParsedQuestions`
 Mockea `llmClient.chat()` para devolver `{"questions": ["q1", "q2", "q3"]}`
 Verifica que el resultado es una List de 3 strings

- `generateRefinementQuestions_shouldThrowPipelineException_onInvalidJson`
 Mockea `llmClient.chat()` para devolver `"not valid json"`
 Verifica que se lanza `PipelineException` con fase "Phase0"

- `generateRefinementQuestions_shouldHandleMarkdownFencedResponse`
 Mockea `llmClient.chat()` para devolver:
 `
json
{"questions": ["q1"]}
`
 Verifica que el resultado es una List de 1 string

- `generateRefinementQuestions_shouldThrowPipelineException_onEmptyResponse`
 Mockea `llmClient.chat()` para devolver `""`
 Verifica que se lanza `PipelineException` con fase "Phase0"

### Pruebas de buildContext

- `buildContext_shouldReturnEnrichedIdeaContext`
 Mockea `llmClient.chat()` para devolver `"Enriched summary text"`
 Verifica que `IdeaContext.rawIdea()` es igual a `idea.description()`
 Verifica que `IdeaContext.enrichedSummary()` es igual a `"Enriched summary text"`
 Verifica que `IdeaContext.refinements()` es igual a la lista de respuestas proporcionada

- `buildContext_shouldFormatQABlockCorrectly`
 Captura el userMessage pasado a `llmClient.chat()` mediante `ArgumentCaptor`
 Verifica que contiene `"Q: [question]\nA: [answer]"`

- `buildContext_shouldHandleEmptyAnswers`
 Pasa una `List<RefinementQA>` vacía
 Verifica que se devuelve un `IdeaContext` sin excepción

- `buildContext_shouldThrowPipelineException_onEmptyResponse`
 Mockea `llmClient.chat()` para devolver `""`
 Verifica que se lanza `PipelineException` con fase "Phase0"

---

## Criterios de aceptación
1. `mvn clean compile` → SUCCESS
2. `mvn test` → 8 pruebas pasan (GatekeeperOrchestratorTest)
3. `generateRefinementQuestions()` no tiene `UnsupportedOperationException`
4. `buildContext()` no tiene `UnsupportedOperationException`
5. Las respuestas nulas/vacías del LLM lanzan `PipelineException`
6. La RuntimeException de `JsonParser` se envuelve en `PipelineException`
7. Todo el parseo JSON pasa por `JsonParser.parse()`
8. Sin cadenas hardcodeadas fuera de las constantes de system prompt

---

## Pistas de implementación

### Patrón de manejo de excepciones
```java
try {
    llmClient.chat(QUESTIONS_SYSTEM_PROMPT, idea.description());
} catch (PipelineException e) {
    throw e;
} catch (RuntimeException e) {
    throw new PipelineException("Failed to parse questions JSON", "Phase0", 0, e);
}
```

### Auxiliar de parseo JSON
```java
public List<String> parseQuestions(String json) {
    try {
        JsonNode node = JsonParser.parse(json);
        return node.get("questions").asList();
    } catch (RuntimeException e) {
        throw new PipelineException("Failed to parse questions JSON", "Phase0", 0, e);
    }
}
```

### Formateo del bloque QA
```java
StringBuilder qaBlock = new StringBuilder();
for (RefinementQA qa : answers) {
    qaBlock.append("Q: ").append(qa.question()).append("\n");
    qaBlock.append("A: ").append(qa.answer()).append("\n\n");
}
```

---

## Archivos a modificar

1. **src/main/java/com/ideapipeline/orchestrator/GatekeeperOrchestrator.java**
   - Reemplazar `UnsupportedOperationException` con las implementaciones reales
   - Añadir la anotación `@Slf4j`
   - Usar inyección por constructor

2. **src/test/java/com/ideapipeline/orchestrator/GatekeeperOrchestratorTest.java**
   - Crear un archivo de pruebas nuevo
   - Usar Mockito para mockear `LlmClient`
   - Verificar los 8 escenarios de prueba anteriores

3. **pom.xml** (si es necesario)
   - Asegurar que están presentes spring-boot-starter, spring-boot-starter-test, junit-jupiter, mockito-core

---

## Criterios de éxito

Tras la implementación:
- ✅ El código compila sin advertencias
- ✅ Las 8 pruebas pasan
- ✅ No queda ningún `UnsupportedOperationException`
- ✅ Manejo de excepciones adecuado en todo el código
- ✅ Logging en los niveles apropiados

---

## Siguientes pasos

Una vez implementado `GatekeeperOrchestrator.java`:
1. Ejecutar `mvn clean compile` para verificar la compilación
2. Ejecutar `mvn test` para verificar que todas las pruebas pasan
3. Verificar que el endpoint de health del pipeline sigue funcionando
4. Pasar a la implementación de la Fase 1
