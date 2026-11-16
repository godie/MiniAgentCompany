# idea.md

**Version:** 1.2
**Last updated:** 2026-04-10
**Based on:** manually converged design (score 91)


## Idea
A multi-agent debate and convergence system to transform raw ideas
into structured, execution-ready software projects.

## Problem it solves
Going from a vague idea to a project with context documents, flows,
estimated tasks and a defined stack requires multiple sessions, scattered
criteria and undocumented decisions. This system orchestrates specialized
LLMs to make that process systematic, traceable and reproducible.

## Target user
Developers or product owners who want to validate and structure
a software idea before writing a single line of code.

## What it produces
- context.md — vision, problem, scope, constraints
- flows.md — user flows and edge cases
- tasks.md — epics, user stories and technical tasks
- ArchitectureDoc — stack and technical decisions
- TaskGraph — estimated tasks with dependencies and points

## Known constraints
- Manual proof of concept first, automation afterwards
- Stack: Java 21 + Spring Boot 3.3 + Spring AI + virtual threads
- Initial LLM provider: OpenAI (gpt-4o-mini)
- The system is recursive: it used itself to design itself

## Current state
Design pipeline completed manually:
- Claude → Architect + Gatekeeper
- DeepSeek → CriticAgent (Phase 3a)
- ChatGPT → ValidationAgent (Phase 3b)
- design convergenceScore: 93 — FINAL
