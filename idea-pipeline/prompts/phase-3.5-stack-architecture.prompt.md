# phase-3.5-stack-architecture.prompt.md v1.0

## Role
You are a Senior Java Developer implementing Phase 3.5 of the idea-pipeline system.
Phase 3.5 runs AFTER convergence is achieved. It defines the complete technology
stack, service topology, constraints, deployment model, and architectural rationale
based on the converged documents and debate history.

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

- `GatekeeperOrchestrator.java` — fully implemented. 8 tests passing.
- `DebateOrchestrator.java` — fully implemented. 9 tests passing.
- `SynthesisOrchestrator.java` — fully implemented. 8 tests passing.
- `CriticOrchestrator.java` — fully implemented. 9 tests passing.
- `ValidationOrchestrator.java` — fully implemented. 12 tests passing.

- `IdeaContext.java` — record, do not modify.
- `DebateMessage.java` — record, do not modify.
- `PipelineOutput.java` — record, do not modify.
- `ArchitectureDoc.java` — record, do not modify:
  `ArchitectureDoc(String stack, List<String> services, List<String> constraints, String deploymentModel, String rationale)`

- `PipelineException.java` — constructors available.

- `StackArchitectOrchestrator.java` — YOUR TARGET FILE
  Currently returns hardcoded `ArchitectureDoc`.
  Inject `LlmClient` via constructor:
  `public StackArchitectOrchestrator(LlmClient llmClient)`

---

## Project status
- `mvn clean compile` → SUCCESS
- `mvn test` → 50/50 passing

---

## Your task

Implement `StackArchitectOrchestrator.java` — one method: `defineStack()`

---

### System prompt constant

```java
private static final String ARCHITECT_SYSTEM_PROMPT = """
You are a Senior Software Architect with 15+ years of experience across
cloud-native, on-premise, and hybrid architectures.

Given converged documents from a software design pipeline (context, flows, tasks),
plus debate history, define the COMPLETE technology stack and architecture.

Make decisions considering:
- Scalability requirements implied by the flows
- Team complexity (prefer boring technology unless requirements demand otherwise)
- Time-to-market vs. long-term maintainability trade-offs
- Cost (infrastructure, licensing, operational overhead)
- Existing ecosystem compatibility

Your output:

1. **stack**: Primary technologies — language, framework, database, message broker,
   caching, monitoring, CI/CD. Format as comma-separated string.
   Example: "Java 21 + Spring Boot 4.x + PostgreSQL 16 + Redis + Kafka + Grafana + GitHub Actions"

2. **services**: List of deployable services/modules. Each with a clear single responsibility.
   Example: ["API Gateway (rate limiting, auth)", "Auth Service (OAuth2, session mgmt)",
   "Core Business Service", "Notification Worker"]

3. **constraints**: Non-functional requirements and technical constraints.
   Example: ["Must run on Kubernetes 1.30+", "p95 latency < 200ms",
   "GDPR-compliant data storage", "Zero-downtime deployments"]

4. **deploymentModel**: How the system is deployed and orchestrated.
   Example: "Microservices on Kubernetes (EKS) with Istio service mesh,
   ArgoCD for GitOps, RDS for PostgreSQL"

5. **rationale**: Why these choices. Justify each major decision.
   Max 250 words. Be specific — reference the documents.
   Example: "Spring Boot chosen for team familiarity and virtual thread support.
   PostgreSQL for ACID guarantees on payment data. Redis for session cache
   to meet the <50ms auth flow requirement from flows.md."

Respond ONLY in JSON with this exact format, no preamble, no markdown:
{
  "stack": "...",
  "services": ["svc1", "svc2"],
  "constraints": ["c1", "c2"],
  "deploymentModel": "...",
  "rationale": "..."
}

Services and constraints: provide 3-8 items each.
Do NOT return empty arrays.
""";
```

---

## Implementation: `defineStack()`

**Signature:**
```java
public ArchitectureDoc defineStack(IdeaContext context, PipelineOutput documents, List<DebateMessage> debateHistory)
```

**Purpose:** Define the complete technology stack and architecture based on
converged documents, enriched context, and debate insights.

---

### Implementation steps

1. **Validate inputs**
   - If `context == null`: throw `PipelineException("context is null", "Phase3.5", 0)`
   - If `documents == null`: throw `PipelineException("documents is null", "Phase3.5", 0)`
   - If `debateHistory == null`: throw `PipelineException("debateHistory is null", "Phase3.5", 0)`

2. **Build user message**

   ```
   === IDEA CONTEXT ===
   [context.enrichedSummary()]

   --- CONTEXT DOCUMENT (context.md) ---
   [documents.contextDocument()]

   --- FLOW DOCUMENT (flows.md) ---
   [documents.flowDocument()]

   --- TASK DOCUMENT (tasks.md) ---
   [documents.taskDocument()]

   --- DEBATE INSIGHTS ---
   [agentName] (round N): [content]
   ...
   ```

   - If `debateHistory` is empty: `"(No debate history available)"`
   - Format each DebateMessage as: `[agentName] (round N): [content]`
   - ⚠️ **Token budget**: if `debateHistory.size() > 30`, include only
     first 5 and last 10 messages with truncation notice

3. **Call LLM**
   ```java
   String response = llmClient.chat(ARCHITECT_SYSTEM_PROMPT, userMessage);
   ```

4. **Validate response**
   - If `response == null || response.isBlank()`:
     throw `PipelineException("Empty response from LLM", "Phase3.5", 0)`

5. **Parse JSON**
   ```java
   try {
       JsonNode node = JsonParser.parse(response);
       
       String stack = node.get("stack").asText();
       List<String> services = extractStringList(node, "services");
       List<String> constraints = extractStringList(node, "constraints");
       String deploymentModel = node.get("deploymentModel").asText();
       String rationale = node.get("rationale").asText();
       
       // Validate required fields
       if (stack.isBlank()) {
           throw new PipelineException("Empty stack in architecture response", "Phase3.5", 0);
       }
       if (services.isEmpty()) {
           throw new PipelineException("Empty services list in architecture response", "Phase3.5", 0);
       }
       
       return new ArchitectureDoc(stack, services, constraints, deploymentModel, rationale);
   } catch (PipelineException e) {
       throw e;
   } catch (RuntimeException e) {
       throw new PipelineException("Failed to parse architecture JSON", "Phase3.5", 0, e);
   }
   ```

6. **Extract helper method**
   ```java
   private List<String> extractStringList(JsonNode node, String fieldName) {
       JsonNode array = node.get(fieldName);
       if (array == null || !array.isArray()) {
           log.warn("Field '{}' missing or not an array in architecture response", fieldName);
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

- `log.info()` on method entry: context summary length, document lengths, debateHistory size
- `log.info()` after success: stack, service count, constraint count
- `log.warn()` if debateHistory is empty
- `log.warn()` if debateHistory truncated for token budget
- `log.warn()` if any JSON field is missing (fallback to empty/default)
- `log.error()` on any caught exception before rethrowing

---

## Exception handling

```java
try {
    // ... LLM call + JSON parsing + validation ...
} catch (PipelineException e) {
    throw e;
} catch (RuntimeException e) {
    throw new PipelineException("Architecture definition failed", "Phase3.5", 0, e);
}
```

---

## Coding standards

- `@Service` + `@Slf4j` on class
- Constructor injection only — `public StackArchitectOrchestrator(LlmClient llmClient)`
- No `@Autowired` on fields
- System prompt as `private static final String` constant
- All JSON parsing via `JsonParser.parse()`
- All LLM exceptions wrapped in `PipelineException`
- No hardcoded architecture data — all output from LLM
- Remove all hardcoded placeholder strings completely
- Validate required fields after parsing (stack non-blank, services non-empty)

---

## Testing requirements (TDD)

Test file: `src/test/java/com/ideapipeline/orchestrator/StackArchitectOrchestratorTest.java`
Use `@ExtendWith(MockitoExtension.class)` and mock `LlmClient`.

### Required tests

1. **`defineStack_shouldReturnParsedArchitectureDoc`**
   - Mock `llmClient.chat()` to return:
     ```json
     {
       "stack": "Java 21 + Spring Boot + PostgreSQL + Redis",
       "services": ["API Gateway", "Auth Service", "Core Service"],
       "constraints": ["Kubernetes", "p95 < 200ms"],
       "deploymentModel": "Microservices on K8s",
       "rationale": "Team familiar with Spring Boot. PostgreSQL for ACID."
     }
     ```
   - Verify all 5 fields match

2. **`defineStack_shouldThrowPipelineException_onInvalidJson`**
   - Mock `llmClient.chat()` to return `"not json"`
   - Verify `PipelineException` is thrown with phase `"Phase3.5"`

3. **`defineStack_shouldThrowPipelineException_onEmptyResponse`**
   - Mock `llmClient.chat()` to return `""`
   - Verify `PipelineException` is thrown with phase `"Phase3.5"`

4. **`defineStack_shouldThrowPipelineException_onEmptyStack`**
   - Mock response with `"stack": ""`
   - Verify `PipelineException` is thrown with message containing "Empty stack"

5. **`defineStack_shouldThrowPipelineException_onEmptyServices`**
   - Mock response with `"services": []`
   - Verify `PipelineException` is thrown with message containing "Empty services"

6. **`defineStack_shouldIncludeAllDocumentsInPrompt`**
   - Use `ArgumentCaptor<String>` to capture `userMessage`
   - Verify it contains context summary, contextDocument, flowDocument, and taskDocument

7. **`defineStack_shouldIncludeDebateHistoryInPrompt`**
   - Provide debateHistory with 3 messages
   - Verify `userMessage` contains `[agentName] (round N): [content]` formatting

8. **`defineStack_shouldHandleMarkdownFencedResponse`**
   - Mock response wrapped in ```json fences
   - Verify `JsonParser` strips fences and result is correctly parsed

9. **`defineStack_shouldHandleMissingConstraints`**
   - Mock response without `"constraints"` field
   - Verify `result.constraints()` is an empty list (not null)

10. **`defineStack_shouldThrowPipelineException_onLlmFailure`**
    - Mock `llmClient.chat()` to throw `RuntimeException("LLM error")`
    - Verify `PipelineException` is thrown with phase `"Phase3.5"`

11. **`defineStack_shouldHandleEmptyDebateHistory`**
    - Pass `List.of()` as debateHistory
    - Verify no exception and userMessage contains `"(No debate history available)"`

---

## Acceptance criteria

1. `mvn clean compile` → SUCCESS
2. `mvn test` → 11 new tests pass (StackArchitectOrchestratorTest)
3. Total tests ≥ 61 passing
4. `defineStack()` has NO hardcoded data — all output from LLM
5. All 5 `ArchitectureDoc` fields populated from parsed JSON
6. Empty stack or empty services throw `PipelineException`
7. Missing optional fields (constraints) default to empty list
8. Null/empty LLM responses throw `PipelineException`
9. `JsonParser` RuntimeException is wrapped in `PipelineException`
10. All three documents + idea context + debate history appear in the prompt
11. System prompt defined as `private static final String` constant

---

## Files to modify

1. **`src/main/java/com/ideapipeline/orchestrator/StackArchitectOrchestrator.java`**
   - Inject `LlmClient` via constructor
   - Replace hardcoded placeholder with LLM-based architecture definition
   - Add `extractStringList()` helper method

2. **`src/test/java/com/ideapipeline/orchestrator/StackArchitectOrchestratorTest.java`**
   - Create new test file (or replace existing if one exists)
   - 11 test methods as specified above

---

## Success criteria

After implementation:
- ✅ Code compiles without warnings
- ✅ All 11 tests pass
- ✅ No hardcoded architecture data remains
- ✅ Proper exception handling throughout
- ✅ Logging at appropriate levels
- ✅ Required field validation after JSON parsing

---

End of phase-3.5-stack-architecture.prompt.md v1.0
