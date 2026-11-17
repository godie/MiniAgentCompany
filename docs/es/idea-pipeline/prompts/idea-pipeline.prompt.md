# idea-pipeline.prompt.md v1.0

1. Validación de team.members() vacío — añadir en el bloque de validación inicial:
javaif (team == null || team.members() == null || team.members().isEmpty()) {
    throw new PipelineException("team is null or empty", "Phase4", 0);
}
2. Nombre de getDebateRounds() — verificar en PipelineProperties.java si el método se llama getDebateRounds() o getDefaultDebateRounds() y usar el nombre real. Si no existe ninguno, crearlo.

## Rol
Eres un Desarrollador Java Senior que implementa `IdeaPipeline.java` — el orquestador principal del sistema idea-pipeline. Esta clase **no es un agente LLM**: es código Java puramente determinista que coordina todas las fases en orden, gestiona el bucle de convergencia y produce el `PipelineResult` final.

## Contexto
Lee estos archivos antes de escribir cualquier código:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

---

## Lo que ya existe — no modificar

- Todos los orquestadores — completamente implementados y probados:
  - `GatekeeperOrchestrator` — métodos: `generateQuestions(RawIdea)`, `enrichContext(RawIdea, List<RefinementQA>)`
  - `DebateOrchestrator` — método: `runDebate(IdeaContext, int rounds)`
  - `SynthesisOrchestrator` — método: `synthesize(IdeaContext, List<DebateMessage>)`
  - `CriticOrchestrator` — método: `critique(PipelineOutput, List<DebateMessage>)`
  - `ValidationOrchestrator` — método: `validate(PipelineOutput, CritiqueResult, int loopCount)`
  - `StackArchitectOrchestrator` — método: `defineStack(IdeaContext, PipelineOutput, List<DebateMessage>)`
  - `ScrumMasterOrchestrator` — método: `estimateTasks(List<Task>, ArchitectureDoc, Team)`
- `PipelineProperties.java` — inyectar para leer:
  - `getConvergenceThreshold()` → int (por defecto 75)
  - `getMaxLoops()` → int (por defecto 3)
  - `getDebateRounds()` → int (por defecto 2)
- `TaskParser.java` — ya existe:
  - `public static List<Task> parse(String taskDocument)` — parsea el markdown de `tasks.md` a `List<Task>`
- Todos los records — no modificar:
  - `RawIdea(String description)`
  - `RefinementQA(String question, String answer)`
  - `IdeaContext(String rawIdea, List<RefinementQA> refinements, String enrichedSummary)`
  - `DebateMessage(String agentName, String content, int round)`
  - `PipelineOutput(String contextDocument, String flowDocument, String taskDocument)`
  - `CritiqueResult(List<String> critiques, List<String> gaps, List<String> refinementNeeds)`
  - `ValidationResult(int convergenceScore, boolean shouldLoop, String reasoning)`
  - `ArchitectureDoc(String stack, List<String> services, List<String> constraints, String deploymentModel, String rationale)`
  - `Task(String id, String title, String description, String epicId, String userStory)`
  - `TaskGraph(List<EstimatedTask> tasks, Map<String, List<String>> adjacency, int totalPoints, Map<String, Integer> pointsByRole)`
  - `PipelineState(IdeaContext context, List<DebateMessage> debateHistory, PipelineOutput output, int loopCount)`
  - `PipelineResult(IdeaContext context, PipelineOutput documents, ArchitectureDoc architecture, TaskGraph taskGraph, int loopsRequired)`
- `PipelineException.java` — constructores:
  - `PipelineException(String message, String phase, int loopCount)`
  - `PipelineException(String message, String phase, int loopCount, Throwable cause)`
- `IdeaPipeline.java` — TU ARCHIVO OBJETIVO.
  Inyectar todos los orquestadores y `PipelineProperties` por constructor.

## Estado del proyecto
- mvn clean compile → SUCCESS
- mvn test → 85/85 pasando

---

## Tu tarea

Implementar `IdeaPipeline.java` — un método público: `run()`

---

## Método: run()

**Firma:**
```java
public PipelineResult run(
    RawIdea rawIdea,
    List<RefinementQA> refinements,
    Team team
)
```

**Propósito:** Ejecutar todas las fases del pipeline en orden, gestionar el bucle de convergencia entre las fases 1–3b y devolver un `PipelineResult` completamente poblado.

---

## Flujo de ejecución completo

### Fase 0 — Construcción del contexto (secuencial)
1. Llamar `gatekeeperOrchestrator.enrichContext(rawIdea, refinements)` → `IdeaContext`

### Fase 1 — Debate (secuencial)
2. Llamar `debateOrchestrator.runDebate(ideaContext, debateRounds)` → `List<DebateMessage>`
   - `debateRounds` proviene de `pipelineProperties.getDebateRounds()`

### Fase 2 — Síntesis de documentos (secuencial)
3. Llamar `synthesisOrchestrator.synthesize(ideaContext, debateHistory)` → `PipelineOutput`

### Fases 3a + 3b — Bucle de crítica + validación
4. Entrar en el bucle de convergencia. Repetir mientras `loopCount < maxLoops`:

   **a.** Llamar `criticOrchestrator.critique(pipelineOutput, debateHistory)` → `CritiqueResult`

   **b.** Llamar `validationOrchestrator.validate(pipelineOutput, critiqueResult, loopCount)` → `ValidationResult`

   **c.** Registrar en el log `convergenceScore` y `shouldLoop`

   **d.** Si `convergenceScore >= convergenceThreshold` O `!shouldLoop`:
   - Salir del bucle — los documentos han convergido

   **e.** Si `loopCount + 1 >= maxLoops`:
   - Registrar advertencia: `"Forcing convergence after {} loops"` con loopCount + 1
   - Salir del bucle — se alcanzó el máximo de iteraciones

   **f.** En caso contrario (el bucle continúa):
   - Enriquecer `IdeaContext`: construir un nuevo `IdeaContext` añadiendo los gaps y refinementNeeds del `CritiqueResult` al `enrichedSummary`:
     ```
     {original enrichedSummary}

     --- Refinement round {loopCount + 1} ---
     Gaps: {gap1}; {gap2}; ...
     Needs: {need1}; {need2}; ...
     ```
   - Volver a ejecutar la Fase 1 con el `IdeaContext` enriquecido → nuevo `debateHistory`
   - Volver a ejecutar la Fase 2 con el `IdeaContext` enriquecido + el nuevo `debateHistory` → nuevo `PipelineOutput`
   - Incrementar `loopCount`

### Fase 3.5 — Arquitectura de stack (secuencial, tras salir del bucle)
5. Llamar `stackArchitectOrchestrator.defineStack(ideaContext, pipelineOutput, debateHistory)` → `ArchitectureDoc`

### Fase 4 — Planning Poker (secuencial)
6. Llamar `TaskParser.parse(pipelineOutput.taskDocument())` → `List<Task>`
7. Llamar `scrumMasterOrchestrator.estimateTasks(tasks, architectureDoc, team)` → `TaskGraph`

### Retorno
8. Devolver `new PipelineResult(ideaContext, pipelineOutput, architectureDoc, taskGraph, loopCount)`

---

## Validación

- Si `rawIdea` es null o `rawIdea.description()` está en blanco:
  lanzar `PipelineException("rawIdea is null or empty", "Phase0", 0)`
- Si `refinements` es null: tratarlo como `Collections.emptyList()` — sin excepción
- Si `team` es null:
  lanzar `PipelineException("team is null", "Phase4", 0)`
- Todas las demás excepciones de los orquestadores se propagan tal cual — no envolverlas

---

## Concurrencia estructurada — NO se usa en esta implementación

La implementación actual es **solo secuencial**. NO usar `StructuredTaskScope`, `Thread.ofVirtual()` ni `CompletableFuture`. Las fases 1 y 2 se ejecutan secuencialmente dentro del bucle. La ejecución en paralelo es una mejora futura — no implementarla ahora.

---

## Registro (logging)

- `log.info()` al entrar: longitud de la descripción de rawIdea + número de miembros del equipo
- `log.info()` al inicio de cada fase: nombre de la fase + loopCount cuando aplique
- `log.info()` tras la Fase 3b en cada iteración: `convergenceScore`, `shouldLoop`, `loopCount`
- `log.warn()` al forzar la convergencia tras maxLoops
- `log.info()` al completarse con éxito: loopCount final + totalPoints del TaskGraph
- `log.error()` ante cualquier excepción capturada antes de relanzarla

---

## Estándares de código
- Solo inyección por constructor — inyectar los 7 orquestadores + `PipelineProperties`
- @Service + @Slf4j
- Sin @Autowired en campos
- `refinements` null se trata como lista vacía — nunca lanzar
- Todas las excepciones de los orquestadores se propagan sin cambios
- `loopCount` empieza en `0` y representa los bucles completados — se pasa directamente a `validationOrchestrator.validate()`
- La variable `ideaContext` se reasigna dentro del bucle al enriquecer — usar una variable local, no un campo

---

## Requisitos de pruebas (TDD)

Archivo de pruebas: `src/test/java/com/ideapipeline/pipeline/IdeaPipelineTest.java`

### Configuración
Mockear los 7 orquestadores y `PipelineProperties`. Configurar los valores por defecto de `PipelineProperties`:
- `getConvergenceThreshold()` → 75
- `getMaxLoops()` → 3
- `getDebateRounds()` → 2

Usar un helper estático `mockValidationResult(int score, boolean shouldLoop)` para construir mocks de `ValidationResult` de forma limpia en todas las pruebas.

### Pruebas requeridas

- `run_shouldExecuteFullPipelineAndReturnResult`
  Mockear todos los orquestadores con retornos válidos. Mockear `ValidationResult` con score 85, shouldLoop false.
  Verificar que `PipelineResult` no es null, que `loopsRequired == 0` y que `taskGraph` no es null.

- `run_shouldCallPhasesInOrder`
  Usar el verificador `InOrder` de Mockito.
  Verificar el orden de llamadas: `enrichContext` → `runDebate` → `synthesize` → `critique` → `validate` → `defineStack` → `estimateTasks`.

- `run_shouldLoopOnceThenConverge`
  La primera llamada a `validate()` devuelve score 50, shouldLoop true.
  La segunda llamada a `validate()` devuelve score 85, shouldLoop false.
  Verificar que `runDebate` se llama dos veces, `synthesize` dos veces y `loopsRequired == 1`.

- `run_shouldForceConvergenceAfterMaxLoops`
  Todas las llamadas a `validate()` devuelven score 40, shouldLoop true. maxLoops = 3.
  Verificar que `runDebate` se llama 3 veces en total (1 inicial + 2 bucles — el bucle se ejecuta maxLoops veces pero sale antes de volver a ejecutarse tras la última).
  Verificar que `defineStack` sigue llamándose tras la convergencia forzada.
  Verificar que `loopsRequired == 2`.

- `run_shouldEnrichIdeaContextWithGapsOnLoop`
  La primera `validate()` devuelve score 50, shouldLoop true, con un `CritiqueResult` que contiene gaps `["gap1"]` y refinementNeeds `["need1"]`.
  La segunda `validate()` devuelve score 85, shouldLoop false.
  Usar ArgumentCaptor sobre `runDebate` — capturar el `IdeaContext` pasado en la segunda llamada.
  Verificar que el segundo `IdeaContext.enrichedSummary()` contiene `"gap1"` y `"need1"`.

- `run_shouldPassLoopCountToValidate`
  La primera `validate()` devuelve score 50, shouldLoop true.
  La segunda `validate()` devuelve score 85, shouldLoop false.
  Usar ArgumentCaptor sobre `validate` — verificar que la primera llamada tiene loopCount 0 y la segunda loopCount 1.

- `run_shouldThrowPipelineException_onNullRawIdea`
  Verificar PipelineException con fase `"Phase0"`.

- `run_shouldThrowPipelineException_onBlankRawIdeaDescription`
  Pasar un `RawIdea` con descripción en blanco.
  Verificar PipelineException con fase `"Phase0"`.

- `run_shouldThrowPipelineException_onNullTeam`
  Verificar PipelineException con fase `"Phase4"`.

- `run_shouldTreatNullRefinementsAsEmptyList`
  Pasar `null` en refinements.
  Verificar que no se lanza excepción y que `enrichContext` se llama con éxito.

- `run_shouldPropagateExceptionFromOrchestrator`
  Mockear `synthesisOrchestrator.synthesize()` para lanzar `PipelineException("synthesis failed", "Phase2", 0)`.
  Verificar que la misma excepción se propaga fuera de `run()` sin cambios.

---

## Criterios de aceptación
1. `mvn clean compile` → SUCCESS
2. `mvn test` → 11 pruebas nuevas pasan (IdeaPipelineTest)
3. `mvn test` → 96 pruebas en total pasando
4. `run()` ejecuta las 7 fases en el orden correcto
5. El bucle vuelve a ejecutar las fases 1 y 2 con contexto enriquecido en cada iteración
6. `loopCount` en el `PipelineResult` devuelto refleja los bucles completados (0 si converge en el primer intento)
7. La convergencia forzada tras `maxLoops` sigue avanzando a las fases 3.5 y 4
8. `refinements` null nunca lanza — se trata como lista vacía
9. Las excepciones de los orquestadores se propagan sin cambios — sin reenvolver
10. Sin concurrencia estructurada — solo implementación secuencial
