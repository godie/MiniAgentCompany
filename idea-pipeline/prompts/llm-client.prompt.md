# llm-client.prompt.md

## Role
You are a Senior Java Developer implementing the LLM integration layer
for the idea-pipeline system.

## Context
Read these files before writing any code:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

## Your task
Implement `LlmClientImpl.java` — the single entry point for all LLM calls
in the system. Every orchestrator depends on this class.

## What already exists

`LlmClient.java` — interface, do not modify:

```java
public interface LlmClient {
    String chat(String systemPrompt, String userMessage);
    String chatWithHistory(String systemPrompt, List<DebateMessage> history, String userMessage);
}
```

`LlmClientImpl.java` — your target file. Replace the placeholder bodies.

## Implementation requirements

### chat()
- Build a Prompt with two messages: SystemMessage + UserMessage
- Call chatClient.prompt(prompt).call().content()
- Log: systemPrompt length and userMessage length before calling
- Throw PipelineException("LLM call failed", "LlmClient", 0) on any exception

### chatWithHistory()
- Build a message list:
    1. SystemMessage(systemPrompt)
    2. For each DebateMessage in history:
       UserMessage("[agentName]: content")
    3. UserMessage(userMessage)
- Call chatClient.prompt(new Prompt(messages)).call().content()
- Log: number of history messages and userMessage length before calling
- Throw PipelineException("LLM call failed", "LlmClient", 0) on any exception

### Constructor
- Inject ChatClient via constructor (no @Autowired on fields)
- @Slf4j
- @Component

### ChatClient bean
- Define a @Bean in PipelineConfig.java:
```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder) {
    return builder.build();
}
```

## JSON parsing utility
Add a package-private static helper in a new file:
`com.ideapipeline.client.JsonParser.java`

```java
// Parses LLM response to JsonNode, stripping markdown fences if present
public static JsonNode parse(String response) {
    String clean = response
        .replaceAll("(?s)```json\\s*", "")
        .replaceAll("```", "")
        .trim();
    return new ObjectMapper().readTree(clean);
}
```

This will be used by all orchestrators that expect JSON responses from LLMs.

## application.yml — verify these properties exist
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

## Testing requirements (TDD)

For every method implemented, write tests BEFORE or ALONGSIDE the implementation.
Test file: `src/test/java/com/ideapipeline/client/LlmClientImplTest.java`

### Tests required

- `chat_shouldReturnLlmResponse`
  Mock ChatClient, verify response is returned correctly

- `chat_shouldThrowPipelineException_onLlmFailure`
  Mock ChatClient to throw RuntimeException, verify PipelineException is thrown

- `chatWithHistory_shouldFormatHistoryCorrectly`
  Verify each DebateMessage is formatted as "[agentName]: content"

- `chatWithHistory_shouldIncludeSystemMessageFirst`
  Verify first message in prompt is SystemMessage

### Mocking
Use @ExtendWith(MockitoExtension.class)
Mock ChatClient and its builder chain

## Coding standards
- Constructor injection only
- @Slf4j
- No @Autowired on fields
- No business logic — this class only calls the LLM and returns the response
- All exceptions wrapped in PipelineException

## Acceptance criteria
1. mvn clean compile → SUCCESS
2. LlmClientImpl has no UnsupportedOperationException
3. ChatClient is injected via constructor
4. chatWithHistory formats history as "[agentName]: content"
5. JsonParser.parse() strips markdown fences before parsing
6. PipelineConfig has the ChatClient @Bean
```