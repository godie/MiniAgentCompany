# llm-client.prompt.md

## Rol
Eres un Desarrollador Java Senior implementando la capa de integración con LLM
para el sistema idea-pipeline.

## Contexto
Lee estos archivos antes de escribir cualquier código:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

## Tu tarea
Implementa `LlmClientImpl.java` — el único punto de entrada para todas las llamadas LLM
del sistema. Todos los orchestrators dependen de esta clase.

## Qué ya existe

`LlmClient.java` — interfaz, no la modifiques:

```java
public interface LlmClient {
    String chat(String systemPrompt, String userMessage);
    String chatWithHistory(String systemPrompt, List<DebateMessage> history, String userMessage);
}
```

`LlmClientImpl.java` — tu archivo objetivo. Reemplaza los cuerpos placeholder.

## Requisitos de implementación

### chat()
- Construye un Prompt con dos mensajes: SystemMessage + UserMessage
- Llama a chatClient.prompt(prompt).call().content()
- Loguea: la longitud de systemPrompt y la longitud de userMessage antes de llamar
- Lanza PipelineException("LLM call failed", "LlmClient", 0) ante cualquier excepción

### chatWithHistory()
- Construye una lista de mensajes:
    1. SystemMessage(systemPrompt)
    2. Por cada DebateMessage del historial:
       UserMessage("[agentName]: content")
    3. UserMessage(userMessage)
- Llama a chatClient.prompt(new Prompt(messages)).call().content()
- Loguea: el número de mensajes del historial y la longitud de userMessage antes de llamar
- Lanza PipelineException("LLM call failed", "LlmClient", 0) ante cualquier excepción

### Constructor
- Inyecta ChatClient por constructor (sin @Autowired en campos)
- @Slf4j
- @Component

### Bean de ChatClient
- Define un @Bean en PipelineConfig.java:
```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder) {
    return builder.build();
}
```

## Utilidad de parseo de JSON
Añade un helper estático package-private en un archivo nuevo:
`com.ideapipeline.client.JsonParser.java`

```java
// Convierte la respuesta del LLM a JsonNode, eliminando las vallas de markdown si están presentes
public static JsonNode parse(String response) {
    String clean = response
        .replaceAll("(?s)```json\\s*", "")
        .replaceAll("```", "")
        .trim();
    return new ObjectMapper().readTree(clean);
}
```

Lo usarán todos los orchestrators que esperan respuestas JSON de los LLMs.

## application.yml — verifica que existan estas propiedades
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.7
```

## Requisitos de testing (TDD)

Para cada método implementado, escribe tests ANTES o JUNTO A la implementación.
Archivo de test: `src/test/java/com/ideapipeline/client/LlmClientImplTest.java`

### Tests requeridos

- `chat_shouldReturnLlmResponse`
  Mockea ChatClient, verifica que la respuesta se devuelve correctamente

- `chat_shouldThrowPipelineException_onLlmFailure`
  Mockea ChatClient para lanzar RuntimeException, verifica que se lanza PipelineException

- `chatWithHistory_shouldFormatHistoryCorrectly`
  Verifica que cada DebateMessage se formatea como "[agentName]: content"

- `chatWithHistory_shouldIncludeSystemMessageFirst`
  Verifica que el primer mensaje del prompt es SystemMessage

### Mocking
Usa @ExtendWith(MockitoExtension.class)
Mockea ChatClient y su cadena de builder

## Estándares de código
- Solo inyección por constructor
- @Slf4j
- Sin @Autowired en campos
- Sin lógica de negocio — esta clase solo llama al LLM y devuelve la respuesta
- Todas las excepciones envueltas en PipelineException

## Criterios de aceptación
1. mvn clean compile → SUCCESS
2. LlmClientImpl no tiene UnsupportedOperationException
3. ChatClient se inyecta por constructor
4. chatWithHistory formatea el historial como "[agentName]: content"
5. JsonParser.parse() elimina las vallas de markdown antes de parsear
6. PipelineConfig tiene el @Bean de ChatClient
