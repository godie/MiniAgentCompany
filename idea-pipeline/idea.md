# idea.md

**Versión:** 1.2
**Última actualización:** 2026-04-10
**Basado en:** diseño convergido manualmente (score 91)


## Idea
Sistema multi-agente de debate y convergencia para transformar ideas crudas
en proyectos de software estructurados y listos para ejecutar.

## Problema que resuelve
Pasar de una idea vaga a un proyecto con documentos de contexto, flujos,
tareas estimadas y stack definido requiere múltiples sesiones, criterios
dispersos y decisiones no documentadas. Este sistema orquesta LLMs
especializados para hacer ese proceso sistemático, trazable y reproducible.

## Usuario objetivo
Desarrolladores o product owners que quieren validar y estructurar
una idea de software antes de escribir una sola línea de código.

## Lo que produce
- context.md — visión, problema, alcance, restricciones
- flows.md — flujos de usuario y edge cases
- tasks.md — epics, user stories y tareas técnicas
- ArchitectureDoc — stack y decisiones técnicas
- TaskGraph — tareas estimadas con dependencias y puntos

## Restricciones conocidas
- Prueba de concepto manual primero, automatización después
- Stack: Java 21 + Spring Boot 3.3 + Spring AI + virtual threads
- LLM provider inicial: OpenAI (gpt-4o-mini)
- El sistema es recursivo: se usó a sí mismo para diseñarse

## Estado actual
Pipeline de diseño completado manualmente:
- Claude → Architect + Gatekeeper
- DeepSeek → CriticAgent (Fase 3a)
- ChatGPT → ValidationAgent (Fase 3b)
- convergenceScore del diseño: 93 — FINAL
