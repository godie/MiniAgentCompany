# CONTEXT.md — idea-pipeline

**Version:** 1.3
**Last updated:** 2026-05-25
**Based on:** manually converged design (score 91)

## 1. What it is

A multi-agent debate and convergence system that transforms raw software
ideas into structured, execution-ready projects.

It orchestrates multiple LLMs with specialized roles to produce —
systematically, traceably and reproducibly — the artifacts needed
to start developing a software product.

## 2. Problem it solves

Going from a vague idea to a project with context documents, flows,
estimated tasks and a defined stack requires multiple sessions, scattered
criteria and undocumented decisions.

This system makes that process explicit, iterative and convergent.

## 3. Nature of the system

The system is recursive: it was designed using its own phases manually,
with Claude as Architect+Gatekeeper, DeepSeek as CriticAgent
and Ollama (qwen3.5:4b) as ValidationAgent.

## 4. Fundamental duality

| Nature | Phases | What it produces |
|------------|-------|-------------|
| Generative | 0, 1, 2, 3a, 3b, 3.5 | Documents that converge |
| Executive | 4 | Tasks ready for code agents |

The generative phases are iterated until convergenceScore >= 75 or max 3 loops.
The executive phase runs once on already converged documents.

## 5. Phases

| Phase | Name | Input | Output |
|------|--------|-------|--------|
| 0 | Gatekeeper | RawIdea | Enriched IdeaContext |
| 1 | Debate | IdeaContext | List<DebateMessage> |
| 2 | Synthesis | DebateHistory + IdeaContext | context.md + flows.md + tasks.md |
| 3a | Critique | 3 docs + history | critiques + gaps + refinementNeeds |
| 3b | Validation | 3 docs + output of 3a | ValidationResult (convergenceScore + shouldLoop) |
| 3.5 | Stack Architecture | IdeaContext + 3 docs | ArchitectureDoc |
| 4 | Planning Poker | tasks.md + ArchitectureDoc + Team | TaskGraph |

## 6. Technical stack

- Java 21
- Spring Boot 4.1.0-SNAPSHOT
- Spring AI 2.0.0-SNAPSHOT (Ollama provider — qwen3.5:4b)
- Virtual threads + StructuredTaskScope (Java 21 structured concurrency)
- Maven
- Lombok

## 7. Key design decisions

**Virtual threads over CompletableFuture**
The pipeline is I/O-bound. Virtual threads with StructuredTaskScope
are cleaner, more readable, and the failure of one subtask cancels the others
automatically with ShutdownOnFailure.

**Stack defined BEFORE Planning Poker**
Without a defined stack, task estimates are vague.
The ArchitectAgent defines the stack in Phase 3.5 so that
Planning Poker has real technical context.

**Two signatures in LlmClient**
chat() for phases without history (synthesis, validation, architecture).
chatWithHistory() for phases with accumulated history (debate).

**Automatic convergence**
The system decides whether to loop based on convergenceScore,
not on a fixed limit. If after 3 loops the score does not reach
75, advancement is forced — avoiding infinite loops.

**CriticOrchestrator separate from ValidationOrchestrator**
Phase 3a produces critiques.
Phase 3b evaluates convergence.
They are distinct responsibilities that use the same model (qwen3.5:4b) with different system prompts.

**Simplified Planning Poker**
A single Scrum Master LLM simulates the entire team.
Avoids 100+ calls per execution while preserving the value
of contextualized estimates.

## 8. Package structure

```
com.ideapipeline
├── config/
├── controller/
│   └── dto/
├── model/
│   └── enums/
├── client/
├── orchestrator/
├── pipeline/
└── exception/
```

## 9. Project context files

| File | Purpose |
|---------|-----------|
| META_PIPELINE.md | Source of truth for the pipeline and canonical format |
| CONTEXT.md | This file — architecture and decisions |
| AGENTS.md | Definition of each agent |
| idea.md | The concrete idea that originated the system |
| log.md | Decision log of the orchestrator |

## 10. Current state

✅ **Complete MVP** — All 7 pipeline phases have a real implementation with an LLM (Ollama/qwen3.5:4b).

| Phase | Orchestrator | Tests |
|------|-------------|-------|
| 0 | GatekeeperOrchestrator | 8 |
| 1 | DebateOrchestrator | 7 |
| 2 | SynthesisOrchestrator | 8 |
| 3a | CriticOrchestrator | 10 |
| 3b | ValidationOrchestrator | 12 |
| 3.5 | StackArchitectOrchestrator | 11 |
| 4 | PlanningPokerOrchestrator | 14 |
| Controller + Integration | PipelineControllerTest + IdeaPipelineIntegrationTest | 11 |
| LLM client | LlmClientImpl | 6 |
| **Total** | | **87** |

Pending: integration tests with a real Ollama (the 3 current tests use @MockitoBean LlmClient).
