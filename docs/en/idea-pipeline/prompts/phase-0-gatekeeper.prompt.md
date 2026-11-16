# phase-0-gatekeeper.prompt.md v1.2

## Role
You are a Senior Java Developer implementing Phase 0 of the idea-pipeline system.
Phase 0 is the entry point of the pipeline — it receives a raw idea and produces
an enriched context that all subsequent phases will use.

## Context
Read these files before writing any code:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

## What already exists — do not modify

These files are FULLY IMPLEMENTED and tested:

- LlmClient.java — interface with two methods:
 - chat(String systemPrompt, String userMessage)
 - chatWithHistory(String systemPrompt, List<DebateMessage> history, String userMessage)

- LlmClientImpl.java — @Component, fully implemented with Spring AI ChatClient.
 Constructor: public LlmClientImpl(ChatClient chatClient)
 Throws PipelineException on failure. 6 tests passing.

- JsonParser.java — package com.ideapipeline.client. Static utility.
 Method: public static JsonNode parse(String response)
 Strips
 Throws RuntimeException on invalid JSON.

- `PipelineConfig.java` — has @Bean for ChatClient and ExecutorService virtualThreadExecutor()
 
java
 @Bean
 public ChatClient chatClient(ChatClient.Builder builder) {
 return builder.build();
 }
 

- `RawIdea.java`, `RefinementQA.java`, `IdeaContext.java` — records, do not modify

- `PipelineException.java` — constructor signature:
 `PipelineException(String message, String phase, int loopCount)`
 `PipelineException(String message, String phase, int loopCount, Throwable cause)`

- `GatekeeperOrchestrator.java` — YOUR TARGET FILE
 Currently throws UnsupportedOperationException on all methods.
 Inject LlmClient (the interface) via constructor:
 `public GatekeeperOrchestrator(LlmClient llmClient)`

## Project status
- mvn clean compile → SUCCESS
- mvn spring-boot:run → STARTS
- GET /pipeline/health → 200 OK
- LlmClientImplTest → 6/6 passing

---

## Your task

Implement `GatekeeperOrchestrator.java` — two methods.

---

### Method 1: generateRefinementQuestions()

**Purpose:** Receive a raw idea and return 5-7 focused questions that
clarify the most critical unknowns before debate begins.

**System prompt constant:**
java
private static final String QUESTIONS_SYSTEM_PROMPT = """
 You are a product strategist expert at turning vague ideas into clear specs.
 Your job is to ask the MINIMUM and MOST IMPORTANT questions to understand an idea.
 Maximum 6 questions. Each question must disambiguate something critical.
 Respond ONLY in JSON with this exact format, no preamble, no markdown:
 {"questions": ["question 1", "question 2", ...]}
 """;

**Implementation steps:**
1. If `idea.description()` is null or blank: throw `PipelineException("Empty idea description", "Phase0", 0)`
2. Call `llmClient.chat(QUESTIONS_SYSTEM_PROMPT, idea.description())`
3. If response is null or blank: throw `PipelineException("Empty response from LLM", "Phase0", 0)`
4. Parse response with `JsonParser.parse(response)` — wrap RuntimeException:
 `throw new PipelineException("Failed to parse questions JSON", "Phase0", 0, e)`
5. Extract `questions` array from JsonNode as `List<String>`
6. Return the list

**Logging:**
- `log.info()` on method entry: idea description length
- `log.info()` after parsing: number of questions generated
- `log.error()` on any caught exception before rethrowing

---

### Method 2: buildContext()

**Purpose:** Receive the raw idea + user answers and produce an enriched
IdeaContext that captures essence, problem, target user and constraints.

**System prompt constant:**
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

**Implementation steps:**

1. Format answers as:
 
 Q: [question]
 A: [answer]
 
 joined by double newline
2. Build userMessage:

 Original idea: [idea.description()]

 Refinement answers:
 [formatted QA block]

3. Call `llmClient.chat(CONTEXT_SYSTEM_PROMPT, userMessage)`
4. If response is null or blank: throw `PipelineException("Empty response from LLM", "Phase0", 0)`
5. Return `new IdeaContext(idea.description(), answers, response.trim())`

**Logging:**
- `log.info()` on method entry: idea description length and number of answers
- `log.debug()` before LLM call: formatted userMessage
- `log.error()` on any caught exception before rethrowing

---

## Coding standards
- Constructor injection only — `public GatekeeperOrchestrator(LlmClient llmClient)`
- @Service + @Slf4j
- No @Autowired on fields
- All JSON parsing via `JsonParser.parse()`
- All LLM exceptions wrapped in `PipelineException`
- System prompts defined as private static final String constants
- No hardcoded strings outside of system prompt constants

---

## Testing requirements (TDD)

Test file: `src/test/java/com/ideapipeline/orchestrator/GatekeeperOrchestratorTest.java`
Use `@ExtendWith(MockitoExtension.class)` and mock `LlmClient`.

### generateRefinementQuestions tests

- `generateRefinementQuestions_shouldReturnParsedQuestions`
 Mock `llmClient.chat()` to return `{"questions": ["q1", "q2", "q3"]}`
 Verify result is List of 3 strings

- `generateRefinementQuestions_shouldThrowPipelineException_onInvalidJson`
 Mock `llmClient.chat()` to return `"not valid json"`
 Verify `PipelineException` is thrown with phase "Phase0"

- `generateRefinementQuestions_shouldHandleMarkdownFencedResponse`
 Mock `llmClient.chat()` to return:
 `
json
{"questions": ["q1"]}
`
 Verify result is List of 1 string

- `generateRefinementQuestions_shouldThrowPipelineException_onEmptyResponse`
 Mock `llmClient.chat()` to return `""`
 Verify `PipelineException` is thrown with phase "Phase0"

### buildContext tests

- `buildContext_shouldReturnEnrichedIdeaContext`
 Mock `llmClient.chat()` to return `"Enriched summary text"`
 Verify `IdeaContext.rawIdea()` equals `idea.description()`
 Verify `IdeaContext.enrichedSummary()` equals `"Enriched summary text"`
 Verify `IdeaContext.refinements()` equals the provided answers list

- `buildContext_shouldFormatQABlockCorrectly`
 Capture the userMessage passed to `llmClient.chat()` via `ArgumentCaptor`
 Verify it contains `"Q: [question]\nA: [answer]"`

- `buildContext_shouldHandleEmptyAnswers`
 Pass empty `List<RefinementQA>`
 Verify `IdeaContext` is returned without exception

- `buildContext_shouldThrowPipelineException_onEmptyResponse`
 Mock `llmClient.chat()` to return `""`
 Verify `PipelineException` is thrown with phase "Phase0"

---

## Acceptance criteria
1. `mvn clean compile` → SUCCESS
2. `mvn test` → 8 tests pass (GatekeeperOrchestratorTest)
3. `generateRefinementQuestions()` has no `UnsupportedOperationException`
4. `buildContext()` has no `UnsupportedOperationException`
5. Null/empty LLM responses throw `PipelineException`
6. `JsonParser` RuntimeException is wrapped in `PipelineException`
7. All JSON parsing goes through `JsonParser.parse()`
8. No hardcoded strings outside of system prompt constants

---

## Implementation hints

### Exception handling pattern
```java
try {
    llmClient.chat(QUESTIONS_SYSTEM_PROMPT, idea.description());
} catch (PipelineException e) {
    throw e;
} catch (RuntimeException e) {
    throw new PipelineException("Failed to parse questions JSON", "Phase0", 0, e);
}
```

### JSON parsing helper
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

### QA block formatting
```java
StringBuilder qaBlock = new StringBuilder();
for (RefinementQA qa : answers) {
    qaBlock.append("Q: ").append(qa.question()).append("\n");
    qaBlock.append("A: ").append(qa.answer()).append("\n\n");
}
```

---

## Files to modify

1. **src/main/java/com/ideapipeline/orchestrator/GatekeeperOrchestrator.java**
   - Replace `UnsupportedOperationException` with actual implementations
   - Add `@Slf4j` annotation
   - Use constructor injection

2. **src/test/java/com/ideapipeline/orchestrator/GatekeeperOrchestratorTest.java**
   - Create new test file
   - Use Mockito to mock `LlmClient`
   - Verify all 8 test scenarios above

3. **pom.xml** (if needed)
   - Ensure spring-boot-starter, spring-boot-starter-test, junit-jupiter, mockito-core are present

---

## Success criteria

After implementation:
- ✅ Code compiles without warnings
- ✅ All 8 tests pass
- ✅ No `UnsupportedOperationException` remains
- ✅ Proper exception handling throughout
- ✅ Logging at appropriate levels

---

## Next steps

Once `GatekeeperOrchestrator.java` is implemented:
1. Run `mvn clean compile` to verify compilation
2. Run `mvn test` to verify all tests pass
3. Verify the pipeline health endpoint still works
4. Proceed to Phase 1 implementation
