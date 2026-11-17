# idea.md

**Version:** 1.3
**Last updated:** 2026-04-19
**Based on:** manually converged design (score 91)


## Idea
Multi-agent debate and convergence system to transform raw ideas
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
- Sequential implementation (v1) — complete functional base
- Stack: Java 21 + Spring Boot 4.1 + Spring AI + JPA/H2
- Initial LLM provider: OpenAI (gpt-4o-mini)
- The system is recursive: it was used to design itself

## Current status
Implementation completed — 129 tests passing, 0 failures.
All orchestrators, main orchestrator, CLI and REST API implemented.
REST API exposes: POST /questions, POST /run (async), GET /{id}/status, GET /{id}/result, GET /health.
CLI exposes: --idea, --team, --interactive, --refinements, --output.
Next step: virtual threads v2, deploy.
