# ADR-001: Official LLM provider for the MVP

**Date:** 2026-05-24
**Status:** Accepted
**Decider:** Review of existing code and configuration

---

## Context

The `idea-pipeline` project is a multi-agent system that orchestrates multiple LLMs
with specialized roles. During the manual design phase, Claude, ChatGPT
and DeepSeek were used as per-role models. In the real implementation, the system needs
a concrete LLM provider configured in `application.yml` and `PipelineConfig.java`.

The `pom.xml` includes both starters:
- `spring-ai-starter-model-openai`
- `spring-ai-starter-model-ollama`

---

## Decision

**Ollama (qwen3.5:4b) is the official LLM provider for the MVP.**

### Evidence in the code

1. `application.yml` has OpenAI explicitly disabled (`enable: false`)
   and Ollama configured as the active provider with model `qwen3.5:4b`.

2. `PipelineConfig.java` imports and uses `OllamaChatModel`:
   ```java
   import org.springframework.ai.ollama.OllamaChatModel;
   // ...
   public ChatClient chatClient(OllamaChatModel chatModel) {
       return ChatClient.builder(chatModel).build();
   }
   ```

3. The system is working with Ollama — `mvn clean compile` and `mvn test` pass.

---

## Consequences

### Advantages
- **Local-first**: Ollama runs locally, with no dependency on paid external APIs.
- **No rate limits**: Ideal for a pipeline that may make many sequential
  and parallel calls.
- **Small and fast model**: qwen3.5:4b is lightweight (4B params), fast response.
- **Reproducible**: Any developer with Ollama installed can run the pipeline.

### Disadvantages
- **Limited quality**: qwen3.5:4b has less reasoning capability than
  GPT-4o or Claude 3.5 Sonnet. Outputs may be less precise.
- **Sometimes malformed JSON**: Small models tend to produce invalid JSON
  more often. `JsonParser` handles markdown fences but does not fix
  structurally broken JSON.
- **Does not follow complex instructions as well**: Very long system prompts
  or ones with many constraints may be partially ignored.

### Neutral
- The documents (`AGENTS.md`, `META_PIPELINE.md`) preserve the recommendations
  for the ideal model per role (Claude for architecture, DeepSeek for critique, etc.)
  as conceptual guidance. In production, all roles use qwen3.5:4b with
  different system prompts.

---

## Alternatives considered

### OpenAI (gpt-4o-mini)
- **In favor**: Better output quality, more reliable JSON, documented in the original design.
- **Against**: Requires an API key, has costs, rate limits, network latency.
- **Decision**: Discarded for the MVP for local simplicity. It can be migrated
  by changing `application.yml` and `PipelineConfig` without touching the orchestrators.

### OpenAI (gpt-4o)
- **In favor**: Maximum quality.
- **Against**: High cost for a pipeline that makes dozens of calls.
- **Decision**: Discarded for the MVP.

---

## Future migration

Switching from Ollama to OpenAI requires only:

1. `application.yml`: disable `ollama`, enable `openai`, configure the API key and model.
2. `PipelineConfig.java`: replace `OllamaChatModel` with `OpenAiChatModel`.
3. `pom.xml`: optionally remove `spring-ai-starter-model-ollama`.

The orchestrators (`GatekeeperOrchestrator`, `DebateOrchestrator`, etc.)
depend on the `LlmClient` interface, not on the concrete provider. They do not require changes.

---

## References

- `idea-pipeline/src/main/resources/application.yml`
- `idea-pipeline/src/main/java/com/ideapipeline/config/PipelineConfig.java`
- `idea-pipeline/pom.xml`
- `idea-pipeline/CONTEXT.md` (section 6 — Technical stack)
- `idea-pipeline/META_PIPELINE.md` (section 6 — Roles in execution)
