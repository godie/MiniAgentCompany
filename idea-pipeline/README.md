# Idea Pipeline

**Transform vague ideas into structured, estimable software projects — powered by multi‑agent debate and convergence with LLMs.**

[![Java 21](https://img.shields.io/badge/Java-21-blue)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0--SNAPSHOT-green)](https://spring.io/projects/spring-boot)
[![Tests](https://img.shields.io/badge/tests-129%2F129-brightgreen)](#)


---

## What is this?

Idea Pipeline is a **multi‑agent LLM orchestration system** that takes a raw software idea and produces:

- ✅ A refined and enriched context (vision, users, constraints)
- ✅ Multi‑round debate between specialized agents (Product, Architect, Critic)
- ✅ Three structured documents: `context.md`, `flows.md`, `tasks.md`
- ✅ A convergence loop with critique and validation (score 0–100)
- ✅ A complete definition of the technology stack
- ✅ A fully estimated task graph with story points (Fibonacci scale) and dependency relationships

The pipeline was **designed by using itself** — the original 7-phase architecture was converged manually with Claude (Architect+Gatekeeper), DeepSeek (Critic) and Ollama/qwen3.5:4b (Validator).

---

## The 7 phases

| Phase | Name | Nature | What it does |
|-------|------|--------|-------------|
| **0** | Gatekeeper | Generative | Generates up to 6 refinement questions from a `RawIdea`; enriches the context with the user's answers |
| **1** | Debate | Generative | 3 agents (Product, Architect, Critic) debate over several rounds using `StructuredTaskScope` |
| **2** | Synthesis | Generative | 3 agents in parallel generate `context.md`, `flows.md` and `tasks.md` from the debate history |
| **3a** | Critique | Generative | Audits the 3 documents looking for inconsistencies, gaps and topics that need to be debated again |
| **3b** | Validation | Generative | Scores convergence from 0–100; decides whether to loop (score < 75) or advance |
| **3.5** | Stack Architecture | Generative | Defines the technology stack, services, constraints, deployment model and rationale |
| **4** | Planning Poker | Executive | Parses the tasks, simulates team estimation with a Fibonacci scale and builds the dependency graph |

**Convergence loop:** phases 2–3b repeat until `convergenceScore >= 75` (max. 3 loops). Phase 4 runs once on already converged documents.

---

## Prerequisites

| Dependency | Version | Notes |
|------------|---------|-------|
| **Java** | 21+ | Virtual threads + structured concurrency |
| **Maven** | 3.8+ | Build tool |
| **Ollama** | Latest | Local LLM runtime |
| **qwen3.5:4b** | — | Default model (configurable in `application.yml`) |

### Install and start Ollama

```bash
# Install (macOS)
brew install ollama

# Download the default model
ollama pull qwen3.5:4b

# Start the server (leave it running in another terminal)
ollama serve
```

---

## Quick start

### 1. Clone and build

```bash
cd idea-pipeline
mvn clean compile
```

### 2. Run the application

```bash
mvn spring-boot:run
```

The server starts at `http://localhost:8080`.

### 3. Use the API

**Step 1: Get the refinement questions**

```bash
curl -X POST http://localhost:8080/pipeline/start \
  -H "Content-Type: application/json" \
  -d '{"description": "A habit-tracking app with social accountability and AI coaching"}'
```

Response:
```json
["Who is the primary user persona?", "What platform(s) should it support?", ...]
```

**Step 2: Run the full pipeline with your answers**

```bash
curl -X POST http://localhost:8080/pipeline/run \
  -H "Content-Type: application/json" \
  -d '{
    "idea": {"description": "A habit-tracking app with social accountability and AI coaching"},
    "answers": [
      {"question": "Who is the primary user?", "answer": "Busy professionals 25-45"},
      {"question": "What platform?", "answer": "iOS first, Android later"}
    ],
    "team": {
      "members": [
        {
          "id": "dev-01",
          "name": "Alice",
          "baseRole": "BACKEND_DEV",
          "seniority": "SENIOR",
          "extraResponsibilities": []
        },
        {
          "id": "dev-02",
          "name": "Bob",
          "baseRole": "FRONTEND_DEV",
          "seniority": "MID",
          "extraResponsibilities": []
        }
      ]
    },
    "debateRounds": 2
  }'
```

The response includes a `PipelineResult` with the enriched context, the complete documents, the architecture and the estimated task graph.

### Health check

```bash
curl http://localhost:8080/pipeline/health
# {"status": "ok", "version": "1.0"}
```

---

## API reference

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/pipeline/start` | `POST` | Generates refinement questions for a `RawIdea` |
| `/pipeline/run` | `POST` | Runs the full pipeline (the 7 phases) |
| `/pipeline/health` | `GET` | Health check |

### Default values

- **No team** → Uses a single `FULLSTACK_DEV` with `MID` seniority
- **No answers** → Uses an empty list (phase 0 works only with the raw idea)
- **`debateRounds` ≤ 0** → Uses `pipeline.default-debate-rounds` from the configuration (default: 2)

---

## Configuration

All pipeline parameters are in `src/main/resources/application.yml`:

```yaml
pipeline:
  convergence-threshold: 75    # Minimum score to advance past Phase 3b
  max-loops: 3                 # Max convergence loop iterations
  default-debate-rounds: 2     # Rounds per debate (Phase 1)
  poker-consensus-max-distance: 1  # Max Fibonacci gap for consensus
```

### Change the LLM model

Edit `application.yml`:

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          model: llama3.2:3b   # or any other model you've pulled
          temperature: 0.7
```

---

## Project structure

```
idea-pipeline/
├── src/main/java/com/ideapipeline/
│   ├── Application.java              # Spring Boot entry point
│   ├── config/
│   │   ├── PipelineConfig.java       # Bean definitions (StructuredTaskScope, ExecutorService)
│   │   └── PipelineProperties.java   # YAML binding (pipeline.*)
│   ├── controller/
│   │   └── PipelineController.java   # REST API (start, run, health)
│   ├── model/                        # Records & enums (RawIdea, Team, TaskGraph, etc.)
│   │   └── enums/                    # Seniority, BaseRole
│   ├── client/
│   │   ├── LlmClient.java            # Interface — chat() + chatWithHistory()
│   │   ├── LlmClientImpl.java        # Ollama implementation via Spring AI
│   │   └── JsonParser.java           # Markdown fence stripping + JSON extraction
│   ├── orchestrator/                 # One orchestrator per phase
│   │   ├── GatekeeperOrchestrator.java       # Phase 0
│   │   ├── DebateOrchestrator.java           # Phase 1
│   │   ├── SynthesisOrchestrator.java        # Phase 2
│   │   ├── CriticOrchestrator.java           # Phase 3a
│   │   ├── ValidationOrchestrator.java       # Phase 3b
│   │   ├── StackArchitectOrchestrator.java   # Phase 3.5
│   │   └── PlanningPokerOrchestrator.java    # Phase 4
│   ├── pipeline/
│   │   └── IdeaPipeline.java         # Central orchestrator — wires all phases
│   └── exception/
│       └── PipelineException.java    # Checked exception with phase metadata
├── src/test/java/com/ideapipeline/   # 129 tests across orchestrators, controller & integration
├── prompts/                          # System prompts for each phase
├── docs/
│   ├── phases/                       # Phase specs (PHASE-3A-SPEC.md, etc.)
│   ├── reports/                      # Implementation reports
│   ├── decisions/                    # ADRs (ADR-001, ADR-002)
│   ├── PLAN_SIGUIENTES_PASOS.md
│   ├── PLAN_FASES_3A_4.md
│   └── REVISION_PROYECTO.md
├── META_PIPELINE.md                  # Source of truth — pipeline design & canonical format
├── CONTEXT.md                        # Architecture decisions & project overview
├── AGENTS.md                         # Agent definitions & interaction diagrams
├── pom.xml
└── README.md                         # This file
```

---

## Running the tests

```bash
# Full test suite (129 tests)
mvn test

# Specific test class
mvn test -Dtest=PlanningPokerOrchestratorTest

# Controller + integration tests
mvn test -Dtest=PipelineControllerTest,IdeaPipelineIntegrationTest
```

---

## Key design decisions

- **Virtual threads + `StructuredTaskScope`** instead of `CompletableFuture` — cleaner error propagation with `ShutdownOnFailure`
- **Stack defined before Planning Poker** (phase 3.5 → 4) — estimates need technical context
- **A single Scrum Master simulates the team** (phase 4) — avoids more than 100 LLM calls per run
- **Critique and Validation are separate phases** (3a vs 3b) — different responsibilities with different prompts
- **All agents use Ollama/qwen3.5:4b** with different system prompts — no external API costs
- **Canonical exchange format** (`META_PIPELINE.md` section 5) — reproducible manual execution

---

## How to contribute

### Add a new phase

1. Create a new orchestrator in `com.ideapipeline.orchestrator` following the existing pattern
2. Add its system prompt in `prompts/`
3. Wire it into `IdeaPipeline.java` at the correct position in the sequence
4. Write unit tests following the project conventions (`@ExtendWith(MockitoExtension.class)`, mock of `LlmClient`)
5. Update `META_PIPELINE.md` if the flow changes
6. Update `AGENTS.md` with the new agent's definition and the interaction diagram
7. Run `mvn test` to verify there are no regressions

### Code conventions

- **Java 21 records** for all data models (immutable, auto‑generated accessors)
- **Lombok** to reduce boilerplate (`@Slf4j`, `@RequiredArgsConstructor`)
- **`@ExtendWith(MockitoExtension.class)`** for unit tests (no Spring context)
- **`@SpringBootTest` + `@MockitoBean`** for integration tests
- Javadoc on all classes and public methods
- Tests named `methodName_shouldExpectedBehavior_whenCondition`

### Documentation

- `META_PIPELINE.md` — canonical pipeline design (source of truth)
- `CONTEXT.md` — architecture decisions and general project description
- `AGENTS.md` — agent definitions per phase
- `docs/phases/` — detailed specs per phase
- `docs/reports/` — implementation reports per phase
- `docs/decisions/` — Architecture Decision Records

---

---

*Built with Java 21, Spring Boot, Spring AI and Ollama.*


## Multi-Provider Model Configuration

This project supports the following LLM providers:

| Provider | Default model | Environment variable |
|----------|-------------------|---------------------|
| **OpenAI** | `gpt-4o` | `OPENAI_API_KEY` |
| **Anthropic** | `claude-sonnet-4-20250514` | `ANTHROPIC_API_KEY` |
| **DeepSeek** | `deepseek-chat` | `DEEPSEEK_API_KEY` |
| **Mistral AI** | `mistral-large-latest` | `MISTRAL_AI_API_KEY` |
| **Google Gemini** | `gemini-2.0-flash` | `GOOGLE_GENAI_API_KEY` |
| **Ollama** | `llama3.2` | (local, no API key required) |

## Environment variables

### OpenAI

```bash
export OPENAI_API_KEY=sk-...
```

Configuration options (optional):
```properties
spring.ai.openai.chat.options.model=gpt-4o
spring.ai.openai.chat.options.temperature=0.7
```

### Anthropic

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

Configuration options (optional):
```properties
spring.ai.anthropic.chat.options.model=claude-sonnet-4-20250514
spring.ai.anthropic.chat.options.maxTokens=1024
```

### DeepSeek

```bash
export DEEPSEEK_API_KEY=sk-...
```

Configuration options (optional):
```properties
spring.ai.deepseek.chat.options.model=deepseek-chat
spring.ai.deepseek.chat.options.temperature=0.7
```

### Mistral AI

```bash
export MISTRAL_AI_API_KEY=...
```

Configuration options (optional):
```properties
spring.ai.mistralai.chat.options.model=mistral-large-latest
spring.ai.mistralai.chat.options.temperature=0.7
```

### Google Gemini

```bash
export GOOGLE_GENAI_API_KEY=...
```

Configuration options (optional):
```properties
spring.ai.google.genai.chat.options.model=gemini-2.0-flash
spring.ai.google.genai.chat.options.temperature=0.7
```

### Ollama (local)

No API key required. Make sure Ollama is running locally:

```bash
# List available models
ollama list

# If you don't have the model, install it
ollama pull llama3.2
```

Configuration:
```properties
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=llama3.2
```

## Example `application.yml` file

```yaml
# ============================================
# OPENAI
# ============================================
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}

# ============================================
# ANTHROPIC
# ============================================
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}

# ============================================
# DEEPSEEK
# ============================================
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}

# ============================================
# MISTRAL AI
# ============================================
    mistralai:
      api-key: ${MISTRAL_AI_API_KEY}

# ============================================
# GOOGLE GEMINI
# ============================================
    google:
      genai:
        api-key: ${GOOGLE_GENAI_API_KEY}

# ============================================
# OLLAMA (local)
# ============================================
    ollama:
      base-url: http://localhost:11434
```

> **Note**: Credentials are read from environment variables. Set each `API_KEY` in your shell or in a `.env` file that you load at startup.

## Provider Selector in Code

To use a specific provider in the orchestrators:

```java
// In the LlmClient interface you have:
llmClient.chatAnthropic(systemPrompt, userMessage);      // Claude
llmClient.chatDeepSeek(systemPrompt, userMessage);      // DeepSeek
llmClient.chatOpenAi(systemPrompt, userMessage);        // GPT
llmClient.chatMistral(systemPrompt, userMessage);       // Mistral
llmClient.chatGemini(systemPrompt, userMessage);         // Gemini
llmClient.chatOllama(systemPrompt, userMessage);         // Ollama (default)

// Or generic:
llmClient.chatWithModel(systemPrompt, userMessage, ModelProvider.ANTHROPIC);
```

## Agent to Provider Mapping

According to AGENTS.md, the recommended mapping is:

| Agent | Provider | Notes |
|--------|----------|-------|
| GatekeeperAgent | **Anthropic** | Best for refinement questions |
| ContextEnricherAgent | **Anthropic** | Context synthesis |
| ProductAgent | Any | Maintains the product role |
| ArchitectAgent | Any | Technical reasoning |
| **CriticAgent** | **DeepSeek** | Devil's advocate - critical thinking |
| ContextDocSynthesizer | **Anthropic** | Technical documentation |
| FlowDocSynthesizer | **OpenAI** | Diagrams and flows |
| TaskDocSynthesizer | **Anthropic** | Task decomposition |
| **ValidationCriticAgent** | **DeepSeek** | Docs audit |
| ValidationAgent | **OpenAI** | Convergence evaluation |
| StackArchitectAgent | **Anthropic** | Stack decisions |
| ScrumMasterAgent | **Anthropic** | Task estimation |

## Development

```bash
# Compile
mvn clean compile

# Run tests
mvn test

# Run the application
mvn spring-boot:run
```
