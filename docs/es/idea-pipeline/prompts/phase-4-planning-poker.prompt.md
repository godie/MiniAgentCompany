# phase-4-planning-poker.prompt.md v1.1
---

## Rol
Eres un desarrollador Java Senior que implementa la Fase 4 del sistema idea-pipeline.
La Fase 4 es la única fase **Executive**: recibe una lista de tareas, el documento de
arquitectura y la composición del equipo, y produce un `TaskGraph` con estimaciones de
story points, votos simulados del equipo y dependencias entre tareas.

## Contexto
Lee estos archivos antes de escribir cualquier código:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

---

## Lo que ya existe — no modificar

- `LlmClient.java` — interfaz, completamente implementada. 6 tests pasando.
- `LlmClientImpl.java` — @Component, completamente implementado.
- `JsonParser.java` — `public static JsonNode parse(String response)`
- Todos los orquestadores anteriores — completamente implementados. 63 tests pasando.
- Records — no modificar:
  - `Task(String id, String title, String description, String epicId, String userStory)`
  - `EstimatedTask(Task task, int storyPoints, Map<String, Integer> votes, List<String> dependencies)`
  - `TaskGraph(List<EstimatedTask> tasks, Map<String, List<String>> adjacency, int totalPoints, Map<String, Integer> pointsByRole)`
  - `TeamMember(String id, String name, BaseRole baseRole, Seniority seniority, List<String> extraResponsibilities)`
  - `Team(List<TeamMember> members)` — helpers: `byRole(BaseRole)`, `leads()`
  - `ArchitectureDoc(String stack, List<String> services, List<String> constraints, String deploymentModel, String rationale)`
- Enums — no modificar:
  - `BaseRole`: FRONTEND_DEV, BACKEND_DEV, FULLSTACK_DEV, QA_ENGINEER, DEVOPS, MOBILE_DEV
  - `Seniority`: JUNIOR, MID, SENIOR, LEAD, PRINCIPAL
- `PipelineException.java` — constructores:
  - `PipelineException(String message, String phase, int loopCount)`
  - `PipelineException(String message, String phase, int loopCount, Throwable cause)`
- `ScrumMasterOrchestrator.java` — TU ARCHIVO OBJETIVO.
  Inyecta `LlmClient` mediante constructor.

## Estado del proyecto
- mvn clean compile → SUCCESS
- mvn test → 63/63 pasando

---

## Tu tarea

Implementa `ScrumMasterOrchestrator.java` — un método: `estimateTasks()`

---

## Prompt del sistema — definir como constante private static final String

```java
private static final String SCRUM_MASTER_SYSTEM_PROMPT = """
    You are a Scrum Master who knows the full technical stack and the team.
    You will simulate a Planning Poker session for all tasks provided.

    For each task:
    - Estimate story points using Fibonacci scale only: 1, 2, 3, 5, 8, 13, 21
    - Simulate a vote from each team member (use their name as key, Fibonacci value as value)
    - storyPoints is the consensus (median or most common vote)
    - Only assign dependencies that are real technical blockers

    Rules:
    - Justify estimates >= 8 with a concrete technical risk in the task description
    - votes must include every team member by name
    - adjacency maps each taskId to the list of taskIds that must be completed before it
    - A task with no dependencies maps to an empty array in adjacency
    - pointsByRole maps each BaseRole name to total points of tasks primarily owned by that role
    - All storyPoints values must be from the Fibonacci scale: 1, 2, 3, 5, 8, 13, 21

    Respond ONLY in JSON with this exact format, no preamble, no markdown:
    {
      "tasks": [
        {
          "taskId": "task-id",
          "storyPoints": 5,
          "votes": {"Alice": 5, "Bob": 3}
        }
      ],
      "adjacency": {
        "task-001": ["task-002"],
        "task-002": []
      },
      "totalPoints": 42,
      "pointsByRole": {
        "BACKEND_DEV": 21,
        "FRONTEND_DEV": 13,
        "QA_ENGINEER": 8
      }
    }
    """;
```

> Nota: `dependencies` se eliminó del array `tasks[]` en el formato JSON. `adjacency` es la única fuente de verdad para las dependencias entre tareas.

---

## Método: estimateTasks()

**Firma:**
```java
public TaskGraph estimateTasks(
    List<Task> tasks,
    ArchitectureDoc architecture,
    Team team
)
```

**Propósito:** Enviar todas las tareas, la arquitectura y el equipo al LLM Scrum Master,
parsear la respuesta JSON y construir un `TaskGraph` completamente poblado.

**Formato exacto de userMessage:**

```
=== ARCHITECTURE ===
Stack: {architecture.stack()}
Deployment: {architecture.deploymentModel()}
Services: {service1}, {service2}, ...
Constraints: {constraint1}, {constraint2}, ...
Rationale: {architecture.rationale()}

=== TEAM ===
- {member.name()} | {member.baseRole()} | {member.seniority()}
...

=== TASKS ===
[{task.id()}] {task.title()}
Epic: {task.epicId() or "(none)"} | Story: {task.userStory() or "(none)"}
{task.description() or "(none)"}

[{task.id()}] {task.title()}
...
```

Cada tarea se separa con una línea en blanco. Services y Constraints van separados por comas en una sola línea. Si una lista es null o está vacía, escribe `(none)`. Si `epicId`, `userStory` o `description` es null o está en blanco, renderiza `(none)` para ese campo.

**Ejemplo concreto** — dadas 2 tareas y 2 miembros del equipo:

```
=== ARCHITECTURE ===
Stack: Java 21 + Spring Boot 3.3 + PostgreSQL
Deployment: Docker + AWS ECS
Services: API Gateway, Auth Service
Constraints: Must support 10k concurrent users
Rationale: Chosen for team familiarity and scalability.

=== TEAM ===
- Alice | BACKEND_DEV | SENIOR
- Bob | QA_ENGINEER | MID

=== TASKS ===
[task-001] Implement JWT authentication
Epic: epic-auth | Story: As a user I want to log in securely
Implement JWT token generation and validation using Spring Security.

[task-002] Write integration tests for auth flow
Epic: (none) | Story: (none)
(none)
```

**Pasos de implementación:**

1. Validar las entradas:
   - Si `tasks` es null o está vacía: lanzar `PipelineException("tasks is null or empty", "Phase4", 0)`
   - Si `architecture` es null: lanzar `PipelineException("architecture is null", "Phase4", 0)`
   - Si `team` es null o `team.members()` es null o está vacía: lanzar `PipelineException("team is null or empty", "Phase4", 0)`

2. Construir `userMessage` usando el formato exacto de arriba. Usar helpers privados:
   - `formatList(List<String> items)` — devuelve una cadena separada por comas, o `"(none)"` si es null o está vacía
   - `formatTeam(Team team)` — devuelve cada miembro como `"- name | baseRole | seniority\n"`
   - `formatTasks(List<Task> tasks)` — devuelve cada bloque de tarea separado por `"\n\n"`. Para cada tarea: si `epicId`, `userStory` o `description` es null o está en blanco, renderizar `"(none)"` para ese campo.

3. Llamar a `llmClient.chat(SCRUM_MASTER_SYSTEM_PROMPT, userMessage)`

4. Si la respuesta es null o está en blanco:
   lanzar `PipelineException("Empty response from scrum master LLM", "Phase4", 0)`

5. Parsear con `JsonParser.parse(response)` — envolver RuntimeException:
   lanzar `PipelineException("Failed to parse planning poker JSON", "Phase4", 0, e)`

6. Parsear `adjacency` primero → `Map<String, List<String>>`:
   - Para cada campo del nodo objeto `adjacency`: clave = string taskId, valor = lista de strings taskId
   - Por defecto `Collections.emptyMap()` si el campo falta

7. Parsear el array `tasks` del JSON — para cada elemento construir un `EstimatedTask`:
   - `taskId` → String: buscar el `Task` correspondiente en la lista de entrada mediante `task.id().equals(taskId)` (sensible a mayúsculas). Si no se encuentra, omitir con `log.warn("Unknown taskId in LLM response: {}")`
   - `storyPoints` → int: leer el valor y luego validarlo con el helper `toFibonacci(int value)` (ver abajo). Por defecto `1` si el campo falta.
   - `votes` → `Map<String, Integer>`: iterar el nodo objeto `votes`. Por defecto `Collections.emptyMap()` si falta.
   - `dependencies` → `List<String>`: **no leer del elemento JSON de la tarea**. En su lugar, buscar `adjacency.getOrDefault(taskId, Collections.emptyList())`.

8. Parsear `totalPoints` → int (por defecto `0` si falta)

9. Parsear `pointsByRole` → `Map<String, Integer>`: iterar el nodo objeto. Por defecto `Collections.emptyMap()` si falta.

10. Devolver `new TaskGraph(estimatedTasks, adjacency, totalPoints, pointsByRole)`

**Helper privado: `toFibonacci(int value)`**

```
Valid Fibonacci values: {1, 2, 3, 5, 8, 13, 21}
- If value is already in the set: return as-is
- If value < 1: log.warn("Invalid storyPoints {}, defaulting to 1"); return 1
- If value > 21: log.warn("Invalid storyPoints {}, defaulting to 21"); return 21
- Otherwise: return the nearest Fibonacci value (round up to next Fibonacci)
  Mapping: 4→5, 6→8, 7→8, 9→13, 10→13, 11→13, 12→13, 14→21, ...20→21
  log.warn("Non-Fibonacci storyPoints {}, corrected to {}")
```

**Logging:**
- `log.info()` al entrar: número de tareas + número de miembros del equipo
- `log.info()` después del parseo: totalPoints + número de tareas estimadas
- `log.warn()` por cada taskId desconocido en la respuesta del LLM
- `log.warn()` por cada valor de storyPoints corregido por `toFibonacci()`
- `log.error()` en cualquier excepción capturada antes de relanzarla

---

## Estándares de codificación
- Inyección solo por constructor
- @Service + @Slf4j
- Sin @Autowired en los campos
- Todo el parseo JSON mediante `JsonParser.parse()`
- Prompt del sistema como constante private static final String
- Los campos JSON faltantes usan valores por defecto seguros — nunca null, nunca excepción
- `formatList()`, `formatTeam()`, `formatTasks()`, `toFibonacci()` son métodos helper privados
- La coincidencia de tareas por ID es sensible a mayúsculas
- `adjacency` es la única fuente de verdad para las dependencias — nunca leer `dependencies` del elemento JSON de la tarea

---

## Requisitos de pruebas (TDD)

Archivo de test: `src/test/java/com/ideapipeline/orchestrator/ScrumMasterOrchestratorTest.java`

### Tests requeridos

- `estimateTasks_shouldReturnFullyPopulatedTaskGraph`
  Mock de JSON válido con 2 tareas, adjacency, totalPoints y pointsByRole.
  Verificar `TaskGraph.tasks().size() == 2`, totalPoints, pointsByRole poblados.

- `estimateTasks_shouldMapVotesCorrectly`
  Respuesta mock con `"votes": {"Alice": 5, "Bob": 3}` para una tarea.
  Verificar `EstimatedTask.votes()` == `{"Alice": 5, "Bob": 3}`.

- `estimateTasks_shouldPopulateDependenciesFromAdjacency`
  Mock de adjacency: `{"task-001": ["task-002"], "task-002": []}`.
  Verificar que el `EstimatedTask` de task-001 tiene `dependencies == ["task-002"]`.
  Verificar que el `EstimatedTask` de task-002 tiene `dependencies == []`.
  Confirmar que no se lee ningún campo `dependencies` de los elementos del array `tasks[]`.

- `estimateTasks_shouldSkipUnknownTaskIdWithWarn`
  Respuesta mock con un taskId que no está en la lista de entrada.
  Verificar que la tarea desconocida no se incluye en `TaskGraph.tasks()`.

- `estimateTasks_shouldUseDefaultsForMissingFields`
  Respuesta mock con una entrada de tarea sin `storyPoints` ni `votes`.
  Verificar storyPoints == 1, votes es un mapa vacío.

- `estimateTasks_shouldCorrectNonFibonacciStoryPoints`
  Respuesta mock con `"storyPoints": 4` para una tarea.
  Verificar `EstimatedTask.storyPoints() == 5` (Fibonacci más cercano, redondeando hacia arriba).

- `estimateTasks_shouldCorrectStoryPointsBelowOne`
  Respuesta mock con `"storyPoints": 0`.
  Verificar `EstimatedTask.storyPoints() == 1`.

- `estimateTasks_shouldCorrectStoryPointsAboveTwentyOne`
  Respuesta mock con `"storyPoints": 34`.
  Verificar `EstimatedTask.storyPoints() == 21`.

- `estimateTasks_shouldHandleMarkdownFencedResponse`
  Respuesta mock con vallas ` ```json `.
  Verificar que TaskGraph se parsea correctamente.

- `estimateTasks_shouldThrowPipelineException_onNullTasks`
  Verificar PipelineException con fase `"Phase4"`.

- `estimateTasks_shouldThrowPipelineException_onEmptyTasks`
  Verificar PipelineException con fase `"Phase4"`.

- `estimateTasks_shouldThrowPipelineException_onNullArchitecture`
  Verificar PipelineException con fase `"Phase4"`.

- `estimateTasks_shouldThrowPipelineException_onNullTeam`
  Verificar PipelineException con fase `"Phase4"`.

- `estimateTasks_shouldThrowPipelineException_onEmptyTeamMembers`
  Verificar PipelineException con fase `"Phase4"`.

- `estimateTasks_shouldThrowPipelineException_onEmptyLlmResponse`
  Verificar PipelineException con fase `"Phase4"`.

- `estimateTasks_shouldThrowPipelineException_onInvalidJson`
  Verificar PipelineException con fase `"Phase4"`.

- `estimateTasks_shouldFormatArchitectureInUserMessage`
  Usar ArgumentCaptor.
  Verificar que userMessage contiene `"=== ARCHITECTURE ==="`, `"Stack:"`, `"Deployment:"`.

- `estimateTasks_shouldFormatTeamMembersInUserMessage`
  Pasar un equipo con 2 miembros conocidos.
  Usar ArgumentCaptor.
  Verificar que userMessage contiene `"=== TEAM ==="` y cada miembro como `"- name | ROLE | SENIORITY"`.

- `estimateTasks_shouldFormatTasksInUserMessage`
  Pasar 2 tareas con ids y títulos conocidos.
  Usar ArgumentCaptor.
  Verificar que userMessage contiene `"=== TASKS ==="` y cada tarea formateada como `"[task-id] title"`.

- `estimateTasks_shouldRenderNoneForNullOrEmptyArchitectureLists`
  Pasar `ArchitectureDoc` con services null y constraints vacío.
  Usar ArgumentCaptor.
  Verificar que userMessage contiene `"(none)"` para ambos.

- `estimateTasks_shouldRenderNoneForBlankTaskFields`
  Pasar un `Task` con `epicId` null, `userStory` en blanco y `description` null.
  Usar ArgumentCaptor.
  Verificar que userMessage contiene `"Epic: (none) | Story: (none)"` y `"(none)"` para la descripción.

---

## Criterios de aceptación
1. `mvn clean compile` → SUCCESS
2. `mvn test` → 21 tests nuevos pasan (ScrumMasterOrchestratorTest)
3. `mvn test` → 84 tests en total pasando
4. `estimateTasks()` no tiene `UnsupportedOperationException`
5. Los task IDs desconocidos en la respuesta del LLM se omiten con `log.warn()`, no con excepción
6. Todos los campos de mapa y lista usan por defecto colecciones vacías, nunca null
7. La coincidencia de tareas por ID es sensible a mayúsculas
8. `storyPoints` vale `1` por defecto cuando falta; se corrige al Fibonacci más cercano cuando es inválido
9. `adjacency` es la única fuente de verdad para `EstimatedTask.dependencies()`
10. Todo el parseo JSON mediante `JsonParser.parse()`
11. Prompt del sistema definido como constante private static final

---
