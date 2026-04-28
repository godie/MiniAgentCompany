# idea.md

**Versión:** 1.3
**Última actualización:** 2026-04-19
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
- Implementación secuencial (v1) — base funcional completa
- Stack: Java 21 + Spring Boot 4.1 + Spring AI + JPA/H2
- LLM provider inicial: OpenAI (gpt-4o-mini)
- El sistema es recursivo: se usó a sí mismo para diseñarse

## Estado actual
Implementación completada — 124 tests passing, 0 failures.
Todos los orchestrators, orquestador principal, CLI y REST API implementados.
REST API expone: POST /questions, POST /run (async), GET /{id}/status, GET /{id}/result, GET /health.
CLI expone: --idea, --team, --interactive, --refinements, --output.
Siguiente paso: virtual threads v2, deploy.
