# AGENTS.md — idea-pipeline

**Version:** 1.3
**Last updated:** 2026-05-25
**Based on:** manually converged design (score 95)

## Current implementation
**All agents use Ollama (qwen3.5:4b)** with different system prompts.
The "Recommended model" entries indicate the ideal LLM per profile according to the original design;
they are conceptual recommendations, not the configured provider.

## Format of each agent

---
### [Name]
**Phase**: number and name
**Role**: one sentence
**Recommended model**: suggested LLM (conceptual)
**Input**: what it receives
**Output**: what it produces and in which format
**Parallelism**: solo / parallel with others / sequential
**System prompt summary**: philosophy of the prompt
---

---

### GatekeeperAgent
**Phase**: 0 — Refinement
**Role**: Transforms a raw idea into enriched context by asking the minimum necessary questions.
**Recommended model**: Claude
**Input**: RawIdea (free text)
**Output**: List of 5-7 questions in JSON — `{"questions": ["..."]}`
**Parallelism**: Solo
**System prompt summary**: Product strategist expert in turning vague ideas into clear specs.
Asks the minimum and most important questions. Maximum 6 questions.
Each question disambiguates something critical. Responds only in JSON.

---

### ContextEnricherAgent
**Phase**: 0 — Context Building
**Role**: Generates the enriched executive summary from the idea and the user's answers.
**Recommended model**: Claude
**Input**: RawIdea + List<RefinementQA>
**Output**: String — executive summary of at most 200 words
**Parallelism**: Solo, sequential after GatekeeperAgent
**System prompt summary**: Synthesizes the original idea and the refinement answers into a summary
that captures essence, problem, target user and key constraints.
It is the base context that all subsequent phases receive.

---

### ProductAgent
**Phase**: 1 — Debate
**Role**: Defends the product, user and business value perspective.
**Recommended model**: Any LLM capable of holding a role
**Input**: IdeaContext + snapshot of the debate history
**Output**: DebateMessage — at most 3 sentences per turn
**Parallelism**: Parallel with ArchitectAgent and CriticAgent within each round
**System prompt summary**: Senior Product Manager focused on the user, market and experience.
Asks about the who and the why. References the other agents by name.
Concise and concrete.

---

### ArchitectAgent
**Phase**: 1 — Debate
**Role**: Evaluates technical feasibility, stack, data and integrations.
**Recommended model**: Any LLM with good technical reasoning
**Input**: IdeaContext + snapshot of the debate history
**Output**: DebateMessage — at most 3 sentences per turn
**Parallelism**: Parallel with ProductAgent and CriticAgent within each round
**System prompt summary**: Software Architect focused on technical feasibility and scalability.
Questions unnecessary complexity. References the other agents by name.
Concise and concrete.

---

### CriticAgent (debate)
**Phase**: 1 — Debate
**Role**: Devil's advocate — looks for risks, unvalidated assumptions and non-obvious problems.
**Recommended model**: DeepSeek
**Input**: IdeaContext + snapshot of the debate history
**Output**: DebateMessage — at most 3 sentences per turn
**Parallelism**: Parallel with ProductAgent and ArchitectAgent within each round
**System prompt summary**: Critical thinker who looks for legal, adoption and monetization risks.
Not negative, rigorous. References the other agents by name.
Forces the team to validate assumptions.

---

### ContextDocSynthesizer
**Phase**: 2 — Document Synthesis
**Role**: Generates context.md with vision, problem, users, scope and constraints.
**Recommended model**: Claude
**Input**: IdeaContext + DebateHistory
**Output**: Markdown — sections: Vision, Problem, Target users,
Scope, Constraints, Assumptions, Glossary
**Parallelism**: Parallel with FlowDocSynthesizer and TaskDocSynthesizer
**System prompt summary**: Technical writer who produces clear and precise documentation.
Does not invent — only synthesizes what emerged from the debate.

---

### FlowDocSynthesizer
**Phase**: 2 — Document Synthesis
**Role**: Generates flows.md with user flows, edge cases and Mermaid diagrams.
**Recommended model**: Claude or ChatGPT
**Input**: IdeaContext + DebateHistory
**Output**: Markdown — sections: Main flows, Alternative flows,
Edge cases, Mermaid diagrams
**Parallelism**: Parallel with ContextDocSynthesizer and TaskDocSynthesizer
**System prompt summary**: Business analyst who documents the system's behavior.
Includes happy paths and edge cases identified in the debate.
Uses Mermaid for diagrams.

---

### TaskDocSynthesizer
**Phase**: 2 — Document Synthesis
**Role**: Generates tasks.md with Epics, User Stories and technical tasks with S/M/L estimates.
**Recommended model**: Claude
**Input**: IdeaContext + DebateHistory
**Output**: Markdown — structure: Epics → User Stories → Technical tasks,
each task with description, acceptance criteria and S/M/L estimate
**Parallelism**: Parallel with ContextDocSynthesizer and FlowDocSynthesizer
**System prompt summary**: Tech lead who breaks work down into actionable tasks.
Prioritizes by value and dependencies. Does not generate tasks without acceptance criteria.

---

### ValidationCriticAgent
**Phase**: 3a — Critique
**Role**: Audits the 3 documents looking for inconsistencies, gaps and topics to re-debate.
**Recommended model**: DeepSeek
**Input**: PipelineOutput (3 docs) + DebateHistory
**Output**: JSON — `{"critiques": [], "gaps": [], "refinementNeeds": []}`
**Parallelism**: Solo
**System prompt summary**: Product and architecture auditor. Evaluates quality and consistency
of the documents in the context of the debate that produced them. Does not evaluate
convergence — only identifies problems. Distinguishes between blocking problems and
incremental improvements. The debate history is included in the message to the LLM so
the critiques are contextualised.

---

### ValidationAgent
**Phase**: 3b — Validation + Convergence
**Role**: Decides whether the pipeline converges or loops based on docs and critiques.
**Recommended model**: ChatGPT
**Input**: PipelineOutput (3 docs) + ValidationResult from phase 3a + loopCount
**Output**: JSON — `{"convergenceScore": 0-100, "shouldLoop": boolean, "reasoning": "..."}`
**Parallelism**: Solo, sequential after ValidationCriticAgent
**System prompt summary**: Evaluator who determines the pipeline's convergence.
convergenceScore 0-40: loop mandatory. 41-74: loop recommended. 75-100: advance.
Considers the loopCount to avoid infinite cycles.

---

### StackArchitectAgent
**Phase**: 3.5 — Stack Architecture
**Role**: Defines the complete technology stack based on the converged documents.
**Recommended model**: Claude
**Input**: IdeaContext + PipelineOutput + DebateHistory
**Output**: Markdown — sections: Stack Decision, Services & Components,
Deployment Model, Technical Constraints, Rationale
**Parallelism**: Solo
**System prompt summary**: Senior Software Architect who makes stack decisions
considering scalability, team complexity, time-to-market and costs.
Justifies every decision. Does not choose technology by fashion.

---

### ScrumMasterAgent
**Phase**: 4 — Planning Poker
**Role**: Simulates the entire team estimating tasks with real technical context.
**Recommended model**: Claude
**Input**: List<Task> + ArchitectureDoc + Team
**Output**: JSON — TaskGraph with storyPoints, votes and dependencies per task
**Parallelism**: Solo (simulates parallelism internally)
**System prompt summary**: Scrum Master who knows the stack and the team.
Estimates each task from the perspective of each team role.
Uses the Fibonacci scale. Identifies dependencies between tasks.
Justifies high estimates with concrete technical risks.

---

### IdeaPipeline (orchestrator meta-agent)
**Phase**: All
**Role**: Coordinates the full flow between phases, manages the convergence loop
and decides when to advance.
**Recommended model**: N/A — it is Java code, not an LLM
**Input**: RawIdea + List<RefinementQA> + Team + debateRounds
**Output**: Complete PipelineResult
**Parallelism**: Orchestrates internal parallelism in phases 1, 2 and 4
**System prompt summary**: Not applicable. Its logic is deterministic:
runs phases in order, evaluates convergenceScore after 3b,
loops by enriching the context with gaps if shouldLoop=true,
forces advancement after maxLoops.

---

## Interaction diagram between agents

```
[User / Human orchestrator]
         |
         | RawIdea
         ↓
[GatekeeperAgent] ──questions──→ [User]
         |                            |
         |←────────── answers ────────┘
         |
[ContextEnricherAgent]
         |
         | IdeaContext
         ↓
┌────────────────────────────────────┐
│  PHASE 1 — per round (parallel)    │
│  [ProductAgent]                    │
│  [ArchitectAgent]  → snapshot      │
│  [CriticAgent]                     │
└─────────────┬──────────────────────┘
              | DebateHistory
              ↓
┌────────────────────────────────────┐
│  PHASE 2 (parallel)                │
│  [ContextDocSynthesizer]           │
│  [FlowDocSynthesizer]              │
│  [TaskDocSynthesizer]              │
└─────────────┬──────────────────────┘
              | PipelineOutput (3 docs)
              ↓
[ValidationCriticAgent] — Phase 3a
              |
              | critiques + gaps
              ↓
[ValidationAgent] — Phase 3b
              |
         convergenceScore
         /              \
    < 75                >= 75
    shouldLoop=true     shouldLoop=false
       |                    |
       | enrich context      |
       └──→ PHASE 1 (loop)  ↓
                    [StackArchitectAgent] — Phase 3.5
                            |
                            | ArchitectureDoc
                            ↓
                    [ScrumMasterAgent] — Phase 4
                            |
                            ↓
                       TaskGraph
```

## Convergence loop

**What triggers it**: convergenceScore < 75 in Phase 3b AND loopCount < maxLoops (3)

**What is carried into the loop**: IdeaContext enriched with the gaps and refinementNeeds
identified by ValidationCriticAgent. The previous history is NOT discarded —
it is added as additional context.

**What constitutes convergence**: convergenceScore >= 75. The system does not require
a perfect score — 75 indicates that the documents are coherent enough
to define the stack and estimate tasks.

**Forces advancement**: If loopCount >= 3 regardless of the score.
The pipeline reports "forced convergence" in PipelineResult.

## Adding a new agent

1. Define its role, phase, input and output in this file.
2. Create its system prompt in the corresponding orchestrator as a private constant.
3. If it is a new phase: create its orchestrator in `com.ideapipeline.orchestrator`.
4. Wire it into `IdeaPipeline.java` in the correct order.
5. Update the interaction diagram in this file.
6. Update META_PIPELINE.md if the new phase changes the flow.
