# Idea Pipeline

**Transform vague ideas into structured, estimable software projects — powered by multi-agent LLM debate and convergence.**

[![Java 21](https://img.shields.io/badge/Java-21-blue)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0--SNAPSHOT-green)](https://spring.io/projects/spring-boot)
[![Tests](https://img.shields.io/badge/tests-87%2F87-brightgreen)](#)


---

## What is this?

Idea Pipeline is a **multi-agent LLM orchestration system** that takes a raw software idea and produces:

- ✅ A refined, enriched context (vision, users, constraints)
- ✅ Multi-round debate between specialized agents (Product, Architect, Critic)
- ✅ Three structured documents: `context.md`, `flows.md`, `tasks.md`
- ✅ A convergence loop with critique and validation (score 0–100)
- ✅ A complete technology stack definition
- ✅ A fully estimated task graph with story points (Fibonacci scale) and dependency relationships

The pipeline was **designed using itself** — the original 7-phase architecture was converged manually with Claude (Architect+Gatekeeper), DeepSeek (Critic), and Ollama/qwen3.5:4b (Validator).

---

## The 7 Phases

| Phase | Name | Nature | What it does |
|-------|------|--------|-------------|
| **0** | Gatekeeper | Generative | Generates up to 6 refinement questions from a `RawIdea`; enriches context with user answers |
| **1** | Debate | Generative | 3 agents (Product, Architect, Critic) debate over multiple rounds using `StructuredTaskScope` |
| **2** | Synthesis | Generative | 3 parallel agents generate `context.md`, `flows.md`, and `tasks.md` from the debate history |
| **3a** | Critique | Generative | Audits all 3 documents for inconsistencies, gaps, and topics needing re-debate |
| **3b** | Validation | Generative | Scores convergence 0–100; decides whether to loop (score < 75) or advance |
| **3.5** | Stack Architecture | Generative | Defines the technology stack, services, constraints, deployment model, and rationale |
| **4** | Planning Poker | Executive | Parses tasks, simulates team estimation with Fibonacci scale, builds dependency graph |

**Convergence loop:** Phases 2–3b repeat until `convergenceScore >= 75` (max 3 loops). Phase 4 runs once on converged documents.

---

## Prerequisites

| Dependency | Version | Notes |
|------------|---------|-------|
| **Java** | 21+ | Virtual threads + structured concurrency |
| **Maven** | 3.8+ | Build tool |
| **Ollama** | Latest | Local LLM runtime |
| **qwen3.5:4b** | — | Default model (configurable in `application.yml`) |

### Install & start Ollama

```bash
# Install (macOS)
brew install ollama

# Pull the default model
ollama pull qwen3.5:4b

# Start the server (keep running in a separate terminal)
ollama serve
```

---

## Quick Start

### 1. Clone & build

```bash
cd idea-pipeline
mvn clean compile
```

### 2. Run the application

```bash
mvn spring-boot:run
```

The server starts on `http://localhost:8080`.

### 3. Use the API

**Step 1: Get refinement questions**

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

Response includes `PipelineResult` with enriched context, full documents, architecture, and estimated task graph.

### Health check

```bash
curl http://localhost:8080/pipeline/health
# {"status": "ok", "version": "1.0"}
```

---

## API Reference

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/pipeline/start` | `POST` | Generate refinement questions for a `RawIdea` |
| `/pipeline/run` | `POST` | Execute the full pipeline (all 7 phases) |
| `/pipeline/health` | `GET` | Health check |

### Defaults

- **No team provided** → Falls back to a single `FULLSTACK_DEV` at `MID` seniority
- **No answers provided** → Uses an empty list (Phase 0 runs on raw idea only)
- **`debateRounds` ≤ 0** → Uses `pipeline.default-debate-rounds` from config (default: 2)

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

### Changing the LLM model

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

## Project Structure

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
├── src/test/java/com/ideapipeline/   # 87 tests across orchestrators, controller & integration
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

## Running Tests

```bash
# Full test suite (87 tests)
mvn test

# Specific test class
mvn test -Dtest=PlanningPokerOrchestratorTest

# Controller + integration tests
mvn test -Dtest=PipelineControllerTest,IdeaPipelineIntegrationTest
```

---

## Key Design Decisions

- **Virtual threads + `StructuredTaskScope`** over `CompletableFuture` — cleaner error propagation with `ShutdownOnFailure`
- **Stack defined before Planning Poker** (Phase 3.5 → 4) — estimates need technical context
- **Single Scrum Master simulates the team** (Phase 4) — avoids 100+ LLM calls per execution
- **Critique and Validation are separate phases** (3a vs 3b) — distinct concerns with different prompts
- **All agents use Ollama/qwen3.5:4b** with different system prompts — no external API costs
- **Canonical exchange format** (`META_PIPELINE.md` section 5) — reproducible manual execution

---

## How to Contribute

### Adding a new phase

1. Create a new orchestrator in `com.ideapipeline.orchestrator` following the existing pattern
2. Add its system prompt in `prompts/`
3. Wire it into `IdeaPipeline.java` at the correct position in the sequence
4. Write unit tests following project conventions (`@ExtendWith(MockitoExtension.class)`, mock `LlmClient`)
5. Update `META_PIPELINE.md` if the flow changes
6. Update `AGENTS.md` with the new agent definition and interaction diagram
7. Run `mvn test` to verify no regressions

### Code conventions

- **Java 21 records** for all data models (immutable, auto-generated accessors)
- **Lombok** for boilerplate reduction (`@Slf4j`, `@RequiredArgsConstructor`)
- **`@ExtendWith(MockitoExtension.class)`** for unit tests (no Spring context)
- **`@SpringBootTest` + `@MockitoBean`** for integration tests
- Javadoc on all public classes and methods
- Tests named `methodName_shouldExpectedBehavior_whenCondition`

### Documentation

- `META_PIPELINE.md` — canonical pipeline design (source of truth)
- `CONTEXT.md` — architecture decisions and project overview
- `AGENTS.md` — agent definitions per phase
- `docs/phases/` — detailed specs per phase
- `docs/reports/` — implementation reports per phase
- `docs/decisions/` — Architecture Decision Records

---

---

*Built with Java 21, Spring Boot, Spring AI, and Ollama.*
