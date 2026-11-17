# ADR-002: Parseable format for tasks.md

**Status:** Accepted
**Date:** 2026-05-24
**Deciders:** Codebuff (assistant)

---

## Context

Phase 2 (Synthesis) generates `tasks.md` as a free-form Markdown document. Phase 4 (Planning Poker) needs to parse that document to extract `Task` objects with stable IDs, descriptions, epics, user stories and dependencies.

The original format was too lax for robust parsing:
- No stable or sequential IDs
- No dependencies field
- Inconsistent structure between runs

---

## Decision

Adopt a format of **Markdown enriched with HTML metadata** (`<!-- META: {...} -->`) that is:

1. **Human-readable** — the Markdown looks good in any viewer
2. **Machine-parseable** — the META blocks contain JSON with IDs and dependencies
3. **LLM-parseable** — even without the META comment parser, an LLM can extract the structure from the Markdown

### Canonical format

```markdown
## Epic EPIC-1: [Epic Name]
<!-- META: {"id":"EPIC-1"} -->

### User Story US-1: [User Story Title]
<!-- META: {"id":"US-1","epicId":"EPIC-1"} -->

#### Task TASK-1: [Task Title]
<!-- META: {"id":"TASK-1","epicId":"EPIC-1","userStory":"US-1","dependencies":[]} -->
- **Description:** [1-2 sentence description]
- **Acceptance criteria:**
  - [criterion 1]
  - [criterion 2]
- **Estimate:** S | M | L
- **Dependencies:** None | TASK-X, TASK-Y
```

### Rules

| Rule | Detail |
|-------|---------|
| Sequential IDs | EPIC-1, EPIC-2, ... / US-1, US-2, ... / TASK-1, TASK-2, ... |
| META mandatory | Each heading (Epic, User Story, Task) must have its `<!-- META: {...} -->` block |
| Dependencies | Array of IDs in META + human-readable `- **Dependencies:**` field |
| Estimate | S (Small), M (Medium), L (Large) — for human reference |
| Hierarchy | Epics contain User Stories, User Stories contain Tasks |

---

## Consequences

### Positive
- The Phase 4 parser (LLM-based) can reliably extract tasks with IDs and dependencies
- META comments are invisible in Markdown rendering but parseable
- If the LLM parser fails, a simple regex parser can extract the META blocks as a fallback
- The format is self-documenting: any developer can read it and understand the structure

### Negative
- The Phase 2 prompt is longer (includes the full example)
- It depends on the synthesis LLM following the format strictly
- META comments add visual noise in plain-text editors (but are invisible in rendering)

---

## Alternatives considered

### 1. Pure JSON instead of Markdown
**Rejected** because `tasks.md` must also be readable by the human orchestrator in the manual flow.

### 2. YAML front matter
**Rejected** because it would require an additional YAML parser and is not standard in the project's ecosystem.

### 3. Markdown tables
**Rejected** because tables do not scale well with multi-line acceptance criteria and become unreadable.

### 4. LLM-only parsing with no fixed format
**Rejected** because the parsing LLM may hallucinate IDs or lose dependencies if the format is ambiguous.

---

## Implementation

- **Phase 2** (`SynthesisOrchestrator.TASK_DOC_PROMPT`): updated to generate this format
- **Phase 4** (`PlanningPokerOrchestrator`): will use an LLM parser that understands this format + regex fallback for META comments

---

*End of ADR.*
