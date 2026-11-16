# PHASE-3.5-SPEC: Stack Architecture (Architecture Definition)

**Date:** 2026-05-24
**Version:** 1.0
**Status:** ✅ IMPLEMENTED

---

## 1. Objective

Define the complete technology stack, service topology, non-functional constraints and deployment model based on the converged documents. This phase runs **after** Phase 3b determines convergence.

---

## 2. Inputs

| Input | Type | Source | Description |
|-------|------|--------|-------------|
| `context` | `IdeaContext` | Phase 0 (Gatekeeper) | Enriched context of the original idea |
| `documents` | `PipelineOutput` | Phase 2 (Synthesis) | Three converged documents |
| `debateHistory` | `List<DebateMessage>` | Phase 1 (Debate) | History of the multi-agent debate |

---

## 3. Output

`ArchitectureDoc` — record with 5 fields:

```json
{
  "stack": "Java 21 + Spring Boot + PostgreSQL + Redis",
  "services": ["API Gateway", "Auth Service", "Core Service"],
  "constraints": ["Kubernetes", "p95 < 200ms"],
  "deploymentModel": "Microservices on Kubernetes with Istio",
  "rationale": "Spring Boot for team familiarity. PostgreSQL for ACID."
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `stack` | `String` | ✅ Not blank | Main technology stack (language, framework, DB, message broker, caching, monitoring, CI/CD) |
| `services` | `List<String>` | ✅ Not empty | List of deployable services/modules (3-8 items) |
| `constraints` | `List<String>` | Optional | Non-functional constraints (3-8 items, default `[]`) |
| `deploymentModel` | `String` | ✅ | Deployment and orchestration model |
| `rationale` | `String` | ✅ | Justification of decisions (max 250 words) |

---

## 4. Behavior

### 4.1 System Prompt

The LLM acts as a **senior Software Architect (15+ years)**. It must consider:
- Scalability requirements implicit in the flows
- Team complexity (prefer "boring" technology)
- Time-to-market vs maintainability trade-offs
- Costs (infrastructure, licenses, operational overhead)
- Compatibility with the existing ecosystem

### 4.2 Main flow

1. Validate that `context`, `documents` and `debateHistory` are not `null`
2. Build `userMessage` with:
   - Idea context (enriched summary)
   - Three documents (context.md, flows.md, tasks.md)
   - Formatted debate history
3. Call the LLM with `ARCHITECT_SYSTEM_PROMPT`
4. Parse the JSON response
5. Validate required fields: `stack` not blank, `services` not empty
6. Optional fields (`constraints`) default to `[]`
7. Return `ArchitectureDoc`

### 4.3 Token Budget

Same strategy as Phase 3a: if `debateHistory.size() > 30`, truncate to the first 5 + last 10.

---

## 5. Error handling

| Condition | Exception | Phase | LoopCount |
|-----------|-----------|-------|-----------|
| `context == null` | `PipelineException("context is null", "Phase3.5", 0)` | Phase3.5 | 0 |
| `documents == null` | `PipelineException("documents is null", "Phase3.5", 0)` | Phase3.5 | 0 |
| `debateHistory == null` | `PipelineException("debateHistory is null", "Phase3.5", 0)` | Phase3.5 | 0 |
| Empty/null LLM response | `PipelineException("Empty response from LLM", "Phase3.5", 0)` | Phase3.5 | 0 |
| Empty `stack` in response | `PipelineException("Empty stack in architecture response", "Phase3.5", 0)` | Phase3.5 | 0 |
| Empty `services` in response | `PipelineException("Empty services list in architecture response", "Phase3.5", 0)` | Phase3.5 | 0 |
| Invalid JSON | `PipelineException("Architecture definition failed", "Phase3.5", 0, cause)` | Phase3.5 | 0 |
| LLM error | `PipelineException("Architecture definition failed", "Phase3.5", 0, cause)` | Phase3.5 | 0 |

---

## 6. Acceptance criteria

- [x] `StackArchitectOrchestrator` uses `LlmClient` injected via constructor
- [x] No hardcoded data — the entire architecture comes from the LLM
- [x] System prompt defined as `private static final String`
- [x] Required fields (`stack`, `services`) validated post-parsing
- [x] Missing `constraints` → empty list (not null)
- [x] All 3 documents + idea context + debate history in the prompt
- [x] Token budget for large debate histories
- [x] Successful `mvn compile`
- [x] 11 unit tests pass (`StackArchitectOrchestratorTest`)

---

## 7. Tests

See `StackArchitectOrchestratorTest.java` (11 tests):

| # | Test | What it verifies |
|---|------|------------------|
| 1 | `defineStack_shouldReturnParsedArchitectureDoc` | Parsing of the 5 fields |
| 2 | `defineStack_shouldThrowPipelineException_onInvalidJson` | Invalid JSON → exception |
| 3 | `defineStack_shouldThrowPipelineException_onEmptyResponse` | Empty response → exception |
| 4 | `defineStack_shouldThrowPipelineException_onEmptyStack` | Empty stack → exception |
| 5 | `defineStack_shouldThrowPipelineException_onEmptyServices` | Empty services → exception |
| 6 | `defineStack_shouldIncludeAllDocumentsInPrompt` | All 3 docs + enrichedSummary in prompt |
| 7 | `defineStack_shouldIncludeDebateHistoryInPrompt` | Format `agentName (round N): content` |
| 8 | `defineStack_shouldHandleMarkdownFencedResponse` | JSON inside ```json fences |
| 9 | `defineStack_shouldHandleMissingConstraints` | Missing constraints → [] |
| 10 | `defineStack_shouldThrowPipelineException_onLlmFailure` | RuntimeException → PipelineException |
| 11 | `defineStack_shouldHandleEmptyDebateHistory` | `List.of()` → "(No debate history available)" |

---

## 8. Files involved

| File | Role |
|------|------|
| `StackArchitectOrchestrator.java` | Implementation |
| `StackArchitectOrchestratorTest.java` | 11 unit tests |
| `ArchitectureDoc.java` | Output record (unmodified) |
| `IdeaContext.java` | Input record (unmodified) |
| `PipelineOutput.java` | Input record (unmodified) |

---

*End of spec.*
