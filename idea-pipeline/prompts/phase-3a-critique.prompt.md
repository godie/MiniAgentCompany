# phase-3a-critique.prompt.md v1.0

## Role
You are a Senior Java Developer implementing Phase 3a of the idea-pipeline system.
Phase 3a audits the three synthesized documents (context, flows, tasks) to detect
inconsistencies, gaps, and topics that need to go back to the debate loop.

## Context
Read these files before writing any code:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

---

## What already exists — do not modify

These files are FULLY IMPLEMENTED and tested:

- `LlmClient.java` — interface:
  - `chat(String systemPrompt, String userMessage)`
  - `chatWithHistory(String systemPrompt, List<DebateMessage> history, String userMessage)`

- `LlmClientImpl.java` — @Component, fully implemented. 6 tests passing.

- `JsonParser.java` — package `com.ideapipeline.client`
  `public static JsonNode parse(String response)`
  Strips markdown fences before parsing.

- `GatekeeperOrchestrator.java` — fully implemented. 8 tests passing.
- `DebateOrchestrator.java` — fully implemented. 9 tests passing.
- `SynthesisOrchestrator.java` — fully implemented. 8 tests passing.

- `PipelineOutput.java` — record, do not modify:
  `PipelineOutput(String contextDocument, String flowDocument, String taskDocument)`

- `CritiqueResult.java` — record, do not modify:
  `CritiqueResult(List<String> critiques, List<String> gaps, List<String> refinementNeeds)`

- `DebateMessage.java` — record, do not modify.

- `PipelineException.java` — constructors:
  - `PipelineException(String message, String phase, int loopCount)`
  - `PipelineException(String message, String phase, int loopCount, Throwable cause)`

- `CriticOrchestrator.java` — YOUR TARGET FILE
  Currently returns hardcoded `CritiqueResult`.
  Inject `LlmClient` via constructor:
  `public CriticOrchestrator(LlmClient llmClient)`

---

## Project status
- `mvn clean compile` → SUCCESS
- `mvn test` → 29/29 passing

---

## Your task

Implement `CriticOrchestrator.java` — one method: `critique()`

---

### System prompt constant

```java
private static final String CRITIQUE_SYSTEM_PROMPT = """
You are a product and architecture auditor. Your job is to analyze three
documents — context, flows, and tasks — produced by a software design pipeline
and find:
- **critiques**: specific problems found (contradictions, vague statements,
  missing detail, unrealistic assumptions)
- **gaps**: what is missing entirely (missing flows, missing user roles,
  missing error handling, missing integrations)
- **refinementNeeds**: specific topics that should go back to debate
  (unresolved trade-offs, risky assumptions, unclear scope boundaries)

You are not negative — you are rigorous and constructive.
Every finding must be specific and actionable.

Respond ONLY in JSON with this exact format, no preamble, no markdown:
{
  "critiques": ["critique 1", "critique 2"],
  "gaps": ["gap 1"],
  "refinementNeeds": ["topic to re-debate 1"]
}

Each array should have 3-7 items. Do NOT return empty arrays —
if you truly find nothing, include at least one item acknowledging
the documents are solid in that area.
""";
```

---

## Implementation: `critique()`

**Signature:**
```java
public CritiqueResult critique(PipelineOutput docs, List<DebateMessage> debateHistory)
```

**Purpose:** Audit the three synthesized documents against the debate history
and return structured critique with gaps and refinement needs.

---

### Implementation steps

1. **Validate inputs**
   - If `docs == null`: throw `PipelineException("docs is null", "Phase3a", 0)`
   - If `debateHistory == null`: throw `PipelineException("debateHistory is null", "Phase3a", 0)`

2. **Build user message**

   Format the three documents and debate summary:

   ```
   --- CONTEXT DOCUMENT ---
   [docs.contextDocument()]

   --- FLOW DOCUMENT ---
   [docs.flowDocument()]

   --- TASK DOCUMENT ---
   [docs.taskDocument()]

   --- DEBATE HISTORY (summary) ---
   [agentName] (round N): [content]
   ...
   ```

   - If `debateHistory` is empty, write: `"(No debate history available)"`
   - Format each `DebateMessage` as: `[agentName] (round N): [content]`
   - ⚠️ **Token budget**: If `debateHistory.size() > 30`, include only:
     - First 5 messages
     - Last 10 messages
     - Mark truncation: `... (N messages omitted) ...`

3. **Call LLM**
   ```java
   String response = llmClient.chat(CRITIQUE_SYSTEM_PROMPT, userMessage);
   ```

4. **Validate response**
   - If `response == null || response.isBlank()`:
     throw `PipelineException("Empty response from LLM", "Phase3a", 0)`

5. **Parse JSON**
   ```java
   try {
       JsonNode node = JsonParser.parse(response);
       List<String> critiques = extractStringList(node, "critiques");
       List<String> gaps = extractStringList(node, "gaps");
       List<String> refinementNeeds = extractStringList(node, "refinementNeeds");
       return new CritiqueResult(critiques, gaps, refinementNeeds);
   } catch (RuntimeException e) {
       throw new PipelineException("Failed to parse critique JSON", "Phase3a", 0, e);
   }
   ```

6. **Extract helper method**
   ```java
   private List<String> extractStringList(JsonNode node, String fieldName) {
       JsonNode array = node.get(fieldName);
       if (array == null || !array.isArray()) {
           log.warn("Field '{}' missing or not an array, defaulting to empty list", fieldName);
           return List.of();
       }
       List<String> items = new ArrayList<>();
       for (JsonNode item : array) {
           items.add(item.asText());
       }
       return items;
   }
   ```

---

## Logging

- `log.info()` on method entry: document lengths and debateHistory size
- `log.info()` after success: counts of critiques, gaps, refinementNeeds
- `log.warn()` if debateHistory is empty
- `log.warn()` if debateHistory truncated due to token budget
- `log.warn()` if any JSON field is missing (fallback to empty list)
- `log.error()` on any caught exception before rethrowing

---

## Exception handling

```java
// Pattern for all LLM/parsing failures:
try {
    // ... LLM call + JSON parsing ...
} catch (PipelineException e) {
    throw e;  // re-throw as-is
} catch (RuntimeException e) {
    throw new PipelineException("Critique failed", "Phase3a", 0, e);
}
```

---

## Coding standards

- `@Service` + `@Slf4j` on class
- Constructor injection only — `public CriticOrchestrator(LlmClient llmClient)`
- No `@Autowired` on fields
- System prompt as `private static final String` constant
- All JSON parsing via `JsonParser.parse()`
- All LLM exceptions wrapped in `PipelineException`
- No hardcoded critique strings — all output must come from the LLM
- Remove all hardcoded placeholder logic (`Arrays.asList(...)` etc.)

---

## Testing requirements (TDD)

Test file: `src/test/java/com/ideapipeline/orchestrator/CriticOrchestratorTest.java`
Use `@ExtendWith(MockitoExtension.class)` and mock `LlmClient`.

### Required tests

1. **`critique_shouldReturnParsedCritiqueResult`**
   - Mock `llmClient.chat()` to return:
     ```json
     {"critiques":["c1","c2"],"gaps":["g1"],"refinementNeeds":["r1","r2"]}
     ```
   - Verify result has 2 critiques, 1 gap, 2 refinementNeeds

2. **`critique_shouldThrowPipelineException_onInvalidJson`**
   - Mock `llmClient.chat()` to return `"not valid json"`
   - Verify `PipelineException` is thrown with phase `"Phase3a"`

3. **`critique_shouldThrowPipelineException_onEmptyResponse`**
   - Mock `llmClient.chat()` to return `""`
   - Verify `PipelineException` is thrown with phase `"Phase3a"`

4. **`critique_shouldHandleMarkdownFencedResponse`**
   - Mock `llmClient.chat()` to return:
     ````
     ```json
     {"critiques":["c1"],"gaps":[],"refinementNeeds":[]}
     ```
     ````
   - Verify `JsonParser` strips fences correctly and result has 1 critique

5. **`critique_shouldIncludeAllThreeDocumentsInPrompt`**
   - Use `ArgumentCaptor<String>` to capture the `userMessage`
   - Verify it contains `contextDocument`, `flowDocument`, and `taskDocument` content

6. **`critique_shouldFormatDebateHistoryInUserMessage`**
   - Provide debateHistory with 2 messages
   - Verify `userMessage` contains `[agentName] (round N): [content]` for each

7. **`critique_shouldHandleEmptyDebateHistory`**
   - Pass `List.of()` as debateHistory
   - Verify no exception thrown and userMessage contains `"No debate history available"`

8. **`critique_shouldHandleMissingJsonFields`**
   - Mock `llmClient.chat()` to return `{"critiques":["c1"]}` (missing gaps and refinementNeeds)
   - Verify result has `critiques=["c1"]`, `gaps=[]`, `refinementNeeds=[]`

9. **`critique_shouldThrowPipelineException_onLlmFailure`**
   - Mock `llmClient.chat()` to throw `RuntimeException("LLM error")`
   - Verify `PipelineException` is thrown with phase `"Phase3a"`

---

## Acceptance criteria

1. `mvn clean compile` → SUCCESS
2. `mvn test` → 9 new tests pass (CriticOrchestratorTest)
3. Total tests ≥ 38 passing
4. `critique()` has NO hardcoded data — all output from LLM
5. `CritiqueResult` fields are populated from parsed JSON
6. Null/empty LLM responses throw `PipelineException`
7. `JsonParser` RuntimeException is wrapped in `PipelineException`
8. Debate history is formatted as `[agentName] (round N): [content]`
9. Token budget handling for large debate histories (truncation at 30+ messages)
10. All three documents (context, flow, task) appear in the LLM prompt
11. System prompt defined as `private static final String` constant

---

## Files to modify

1. **`src/main/java/com/ideapipeline/orchestrator/CriticOrchestrator.java`**
   - Inject `LlmClient` via constructor
   - Replace hardcoded placeholder with LLM-based implementation
   - Add `extractStringList()` helper method

2. **`src/test/java/com/ideapipeline/orchestrator/CriticOrchestratorTest.java`**
   - Create new test file (or replace existing if one exists)
   - 9 test methods as specified above

---

## Success criteria

After implementation:
- ✅ Code compiles without warnings
- ✅ All 9 tests pass
- ✅ No hardcoded critique strings remain
- ✅ Proper exception handling throughout
- ✅ Logging at appropriate levels

---

End of phase-3a-critique.prompt.md v1.0
