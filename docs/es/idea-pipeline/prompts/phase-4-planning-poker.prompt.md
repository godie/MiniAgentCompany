# phase-4-planning-poker.prompt.md v1.0

## Rol
Eres un Desarrollador Java Senior que implementa la Fase 4 del sistema idea-pipeline.
La Fase 4 es la fase final. Transforma el documento de tareas (tasks.md) en un
TaskGraph completamente estimado, parseando las tareas desde Markdown y ejecutando una
sesión simulada de Planning Poker con el LLM.

## Contexto
Lee estos archivos antes de escribir cualquier código:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

---

## Lo que ya existe — no modificar

Estos archivos están COMPLETAMENTE IMPLEMENTADOS y probados:

- `LlmClient.java` — interfaz:
  - `chat(String systemPrompt, String userMessage)`
  - `chatWithHistory(String systemPrompt, List<DebateMessage> history, String userMessage)`

- `LlmClientImpl.java` — @Component, completamente implementado. 6 tests pasando.

- `JsonParser.java` — paquete `com.ideapipeline.client`
  `public static JsonNode parse(String response)`

- `GatekeeperOrchestrator.java` — completamente implementado. 8 tests pasando.
- `DebateOrchestrator.java` — completamente implementado. 9 tests pasando.
- `SynthesisOrchestrator.java` — completamente implementado. 8 tests pasando.
- `CriticOrchestrator.java` — completamente implementado. 9 tests pasando.
- `ValidationOrchestrator.java` — completamente implementado. 12 tests pasando.
- `StackArchitectOrchestrator.java` — completamente implementado. 11 tests pasando.

- `Task.java` — record, no modificar:
  `Task(String id, String title, String description, String epicId, String userStory)`
- `EstimatedTask.java` — record, no modificar:
  `EstimatedTask(Task task, int storyPoints, Map<String, Integer> votes, List<String> dependencies)`
- `TaskGraph.java` — record, no modificar:
  `TaskGraph(List<EstimatedTask> tasks, Map<String, List<String>> adjacency, int totalPoints, Map<String, Integer> pointsByRole)`
- `Team.java` — record, no modificar.
- `TeamMember.java` — record, no modificar.
- `ArchitectureDoc.java` — record, no modificar.

- `PipelineException.java` — constructores disponibles.

- `PlanningPokerOrchestrator.java` — TU ARCHIVO OBJETIVO
  Actualmente devuelve tareas hardcodeadas con estimaciones falsas.
  Inyecta `LlmClient` mediante constructor:
  `public PlanningPokerOrchestrator(LlmClient llmClient)`

---

## Estado del proyecto
- `mvn clean compile` → SUCCESS
- `mvn test` → 62/62 pasando

---

## Importante: formato de tasks.md (ADR-002)

Antes de implementar, lee `docs/decisions/ADR-002-tasks-markdown-format.md`.
El tasks.md ahora sigue un formato endurecido con:
- IDs secuenciales: EPIC-1, EPIC-2 / US-1, US-2 / TASK-1, TASK-2
- Bloques de comentario `<!-- META: {"..."} -->` con metadatos JSON (id, epicId, userStory, dependencies)
- Parseable por máquina y legible por humanos

---

## Tu tarea

Implementa `PlanningPokerOrchestrator.java` — un método: `runPlanningPoker()`,
más un helper privado: `parseTasksFromMarkdown()`.

Esta fase tiene DOS responsabilidades:
1. **4A — Parsear** el Markdown de tasks.md en objetos `List<Task>`
2. **4B — Estimar** las tareas mediante Planning Poker simulado con LLM → `TaskGraph`

---

## Fase 4A: Parser de Markdown

### Constante del system prompt

```java
private static final String TASK_PARSE_SYSTEM_PROMPT = """
You are a precise parser. Given a tasks.md document in Markdown,
extract ALL tasks, epics, and user stories.

The Markdown follows this structure:
## Epic N: [Epic Name]
### User Story: [Story Title]
#### Task: [Task Title]
- Description: [description text]
- Acceptance criteria: [criteria text]
- Estimate: S | M | L

Extract every task and return ONLY JSON with this exact format:
{
  "tasks": [
    {
      "id": "TASK-1",
      "title": "task title",
      "description": "description text",
      "epicId": "EPIC-1",
      "userStory": "US-1",
      "sizeEstimate": "S"
    }
  ],
  "epics": [
    {"id": "EPIC-1", "name": "Epic Name"}
  ],
  "userStories": [
    {"id": "US-1", "title": "Story Title", "epicId": "EPIC-1"}
  ]
}

IDs must be sequential: EPIC-1, EPIC-2, ... / US-1, US-2, ... / TASK-1, TASK-2, ...
The size estimate is the original S|M|L from the document.
""";
```

### Implementación: `parseTasksFromMarkdown()`

```java
private List<Task> parseTasksFromMarkdown(String tasksMarkdown) {
    if (tasksMarkdown == null || tasksMarkdown.isBlank()) {
        log.warn("Empty tasks markdown, returning empty task list");
        return List.of();
    }
    
    try {
        String response = llmClient.chat(TASK_PARSE_SYSTEM_PROMPT, tasksMarkdown);
        
        if (response == null || response.isBlank()) {
            throw new PipelineException("Empty response from task parser LLM", "Phase4", 0);
        }
        
        JsonNode node = JsonParser.parse(response);
        JsonNode tasksArray = node.get("tasks");
        
        if (tasksArray == null || !tasksArray.isArray()) {
            log.warn("No tasks array in parser response, returning empty list");
            return List.of();
        }
        
        List<Task> tasks = new ArrayList<>();
        for (JsonNode taskNode : tasksArray) {
            tasks.add(new Task(
                taskNode.get("id").asText(),
                taskNode.get("title").asText(),
                taskNode.get("description").asText(),
                taskNode.get("epicId").asText(),
                taskNode.get("userStory").asText()
            ));
        }
        
        log.info("Parsed {} tasks from markdown", tasks.size());
        return tasks;
        
    } catch (PipelineException e) {
        throw e;
    } catch (RuntimeException e) {
        throw new PipelineException("Failed to parse tasks from markdown", "Phase4", 0, e);
    }
}
```

---

## Fase 4B: Planning Poker mediante LLM

### Constante del system prompt

```java
private static final String PLANNING_POKER_SYSTEM_PROMPT = """
You are a Scrum Master facilitating a Planning Poker session with a
development team. You know the architecture and the team composition.

Your job: estimate story points for each task using Planning Poker.
Use Fibonacci sequence: 1, 2, 3, 5, 8, 13, 21.

For EACH task, provide estimates from the perspective of EVERY team member
who would be involved. Also identify task dependencies.

The team is provided in the user message with their roles and seniority.

The architecture (stack, services, constraints) is also provided.

Rules:
- Story points represent COMPLEXITY + EFFORT + UNCERTAINTY
- Dependencies: a task depends on another if it cannot START before
  the other is DONE
- Higher seniority typically estimates LOWER (more experience)
- QA members estimate higher than devs (they account for testing)
- DevOps members estimate for deployment/infrastructure tasks

Respond ONLY in JSON with this exact format:
{
  "tasks": [
    {
      "taskId": "TASK-1",
      "storyPoints": 5,
      "votes": {
        "memberId1": 5,
        "memberId2": 8,
        "memberId3": 3
      },
      "dependencies": ["TASK-0"]
    }
  ],
  "adjacency": {
    "TASK-0": ["TASK-1", "TASK-2"],
    "TASK-1": ["TASK-3"]
  },
  "totalPoints": 34,
  "pointsByRole": {
    "BACKEND_DEV": 21,
    "FRONTEND_DEV": 13
  }
}

- storyPoints: the FINAL consensus estimate (median of votes, rounded up
  to nearest Fibonacci number)
- votes: map of team member ID → their individual estimate
- dependencies: list of task IDs this task depends on (empty list if none)
- adjacency: dependency graph (parent task → list of child tasks)
- totalPoints: sum of all story points
- pointsByRole: sum of story points grouped by BaseRole enum value

IMPORTANT:
- Every task in the input MUST appear in the output
- Every team member MUST have a vote for each task they'd be involved in
- totalPoints MUST be the sum of all storyPoints values
- pointsByRole sums MUST be consistent with totalPoints
""";
```

---

## Implementación: `runPlanningPoker()`

**Firma:**
```java
public TaskGraph runPlanningPoker(
    String tasksMarkdown,
    Team team,
    ArchitectureDoc architecture
) throws Exception
```

**Propósito:** Parsear las tareas desde Markdown y luego estimarlas mediante
Planning Poker simulado con el equipo y la arquitectura dados.

---

### Pasos de implementación

1. **Validar las entradas**
   - Si `tasksMarkdown == null || tasksMarkdown.isBlank()`:
     lanzar `PipelineException("tasksMarkdown is null or blank", "Phase4", 0)`
   - Si `team == null || team.members() == null || team.members().isEmpty()`:
     lanzar `PipelineException("team is null or empty", "Phase4", 0)`
   - Si `architecture == null`:
     lanzar `PipelineException("architecture is null", "Phase4", 0)`

2. **Parsear las tareas desde Markdown** (Fase 4A)
   ```java
   List<Task> parsedTasks = parseTasksFromMarkdown(tasksMarkdown);
   
   if (parsedTasks.isEmpty()) {
       log.warn("No tasks parsed from markdown, returning empty TaskGraph");
       return new TaskGraph(List.of(), Map.of(), 0, Map.of());
   }
   ```

3. **Construir el mensaje de usuario para el Planning Poker**

   ```
   === TEAM ===
   [team formatted as role+seniority list]

   === ARCHITECTURE ===
   Stack: [architecture.stack()]
   Services: [architecture.services() joined by ", "]
   Deployment: [architecture.deploymentModel()]
   Constraints: [architecture.constraints() joined by ", "]

   === TASKS TO ESTIMATE ===
   [list of tasks formatted as "id: title — description"]
   ```

   Formato del equipo:
   ```
   - [member.id()]: [member.name()] — [member.baseRole()] ([member.seniority()])
     Extra responsibilities: [member.extraResponsibilities() joined by ", "]
   ```

4. **Llamar al LLM**
   ```java
   String response = llmClient.chat(PLANNING_POKER_SYSTEM_PROMPT, userMessage);
   ```

5. **Validar la respuesta**
   - Si `response == null || response.isBlank()`:
     lanzar `PipelineException("Empty response from planning poker LLM", "Phase4", 0)`

6. **Parsear el JSON a `TaskGraph`**
   ```java
   try {
       JsonNode node = JsonParser.parse(response);
       
       // Parsear tareas
       List<EstimatedTask> estimatedTasks = new ArrayList<>();
       JsonNode tasksArray = node.get("tasks");
       for (JsonNode taskNode : tasksArray) {
           String taskId = taskNode.get("taskId").asText();
           int storyPoints = taskNode.get("storyPoints").asInt();
           
           // Buscar el objeto Task original
           Task originalTask = parsedTasks.stream()
               .filter(t -> t.id().equals(taskId))
               .findFirst()
               .orElseThrow(() -> new PipelineException(
                   "Unknown task ID in LLM response: " + taskId, "Phase4", 0));
           
           // Parsear los votos
           Map<String, Integer> votes = new HashMap<>();
           JsonNode votesNode = taskNode.get("votes");
           if (votesNode != null) {
               var fields = votesNode.fields();
               while (fields.hasNext()) {
                   var field = fields.next();
                   votes.put(field.getKey(), field.getValue().asInt());
               }
           }
           
           // Parsear las dependencias
           List<String> deps = new ArrayList<>();
           JsonNode depsNode = taskNode.get("dependencies");
           if (depsNode != null && depsNode.isArray()) {
               for (JsonNode dep : depsNode) {
                   deps.add(dep.asText());
               }
           }
           
           estimatedTasks.add(new EstimatedTask(originalTask, storyPoints, votes, deps));
       }
       
       // Parsear la adyacencia
       Map<String, List<String>> adjacency = new HashMap<>();
       JsonNode adjNode = node.get("adjacency");
       if (adjNode != null) {
           var fields = adjNode.fields();
           while (fields.hasNext()) {
               var field = fields.next();
               List<String> children = new ArrayList<>();
               for (JsonNode child : field.getValue()) {
                   children.add(child.asText());
               }
               adjacency.put(field.getKey(), children);
           }
       }
       
       int totalPoints = node.get("totalPoints").asInt();
       
       // Parsear pointsByRole
       Map<String, Integer> pointsByRole = new HashMap<>();
       JsonNode pbrNode = node.get("pointsByRole");
       if (pbrNode != null) {
           var fields = pbrNode.fields();
           while (fields.hasNext()) {
               var field = fields.next();
               pointsByRole.put(field.getKey(), field.getValue().asInt());
           }
       }
       
       // Validar la consistencia
       int computedTotal = estimatedTasks.stream()
           .mapToInt(EstimatedTask::storyPoints)
           .sum();
       if (computedTotal != totalPoints) {
           log.warn("totalPoints mismatch: declared={}, computed={}", totalPoints, computedTotal);
       }
       
       return new TaskGraph(estimatedTasks, adjacency, totalPoints, pointsByRole);
       
   } catch (PipelineException e) {
       throw e;
   } catch (RuntimeException e) {
       throw new PipelineException("Failed to parse planning poker JSON", "Phase4", 0, e);
   }
   ```

---

## Logging

- `log.info()` al entrar al método: longitud del markdown, tamaño del equipo, resumen de la arquitectura
- `log.info()` después de parsear: número de tareas extraídas
- `log.info()` después del planning poker: totalPoints, número de tareas
- `log.warn()` si no se parsea ninguna tarea desde el markdown
- `log.warn()` si `totalPoints` en la respuesta no coincide con la suma calculada
- `log.error()` ante cualquier excepción capturada antes de relanzarla

---

## Manejo de excepciones

```java
try {
    // ... parsear + estimar ...
} catch (PipelineException e) {
    throw e;
} catch (RuntimeException e) {
    throw new PipelineException("Planning poker failed", "Phase4", 0, e);
}
```

---

## Estándares de código

- `@Service` + `@Slf4j` en la clase
- Solo inyección por constructor — `public PlanningPokerOrchestrator(LlmClient llmClient)`
- Sin `@Autowired` en campos
- System prompts como constantes `private static final String`
- Todo el parseo de JSON mediante `JsonParser.parse()`
- Todas las excepciones del LLM envueltas en `PipelineException`
- Sin tareas ni estimaciones hardcodeadas — toda la salida viene del LLM
- Eliminar toda la lógica de placeholder (`Arrays.asList(...)`, objetos `Task` hardcodeados, votos falsos)
- `parseTasksFromMarkdown()` es un método privado (separación de responsabilidades)
- Validar de forma cruzada el `totalPoints` del LLM contra la suma calculada

---

## Requisitos de testing (TDD)

Archivo de test: `src/test/java/com/ideapipeline/orchestrator/PlanningPokerOrchestratorTest.java`
Usa `@ExtendWith(MockitoExtension.class)` y mockea `LlmClient`.

---

### Tests del parser (Fase 4A)

1. **`parseTasks_shouldExtractAllTasks`**
   - Proporciona markdown con 3 tareas, mockea `llmClient.chat()` para devolver JSON válido
   - Llama a `runPlanningPoker()` y verifica `taskGraph.tasks().size() == 3`

2. **`parseTasks_shouldPreserveEpicAndUserStoryIds`**
   - Verifica que los campos `Task.epicId()` y `Task.userStory()` estén poblados
     desde el JSON parseado

3. **`parseTasks_shouldHandleEmptyMarkdown`**
   - Pasa `""` como tasksMarkdown
   - Verifica que se devuelve un `TaskGraph` con 0 tareas y 0 totalPoints

4. **`parseTasks_shouldThrowPipelineException_onInvalidParseJson`**
   - Mockea el LLM del parser para devolver JSON inválido
   - Verifica que se lanza `PipelineException` con fase `"Phase4"`

---

### Tests del orquestador (Fase 4B)

5. **`runPlanningPoker_shouldReturnParsedTaskGraph`**
   - Mockea el LLM del parser para devolver 2 tareas
   - Mockea el LLM del planning poker para devolver un JSON de TaskGraph válido
   - Verifica que todos los campos estén poblados

6. **`runPlanningPoker_shouldThrowPipelineException_onInvalidJson`**
   - Mockea el LLM del parser para devolver tareas válidas
   - Mockea el LLM del planning poker para devolver JSON inválido
   - Verifica que se lanza `PipelineException`

7. **`runPlanningPoker_shouldThrowPipelineException_onEmptyResponse`**
   - Mockea `llmClient.chat()` para devolver `""` en la llamada del planning poker
   - Verifica que se lanza `PipelineException`

8. **`runPlanningPoker_shouldUseRealTeamMembers`**
   - Usa `ArgumentCaptor<String>` para capturar userMessage
   - Verifica que contiene los nombres, roles y seniority de los miembros del equipo

9. **`runPlanningPoker_shouldUseArchitectureDoc`**
   - Verifica que userMessage contiene el stack, los services, los constraints
     y el deploymentModel de la arquitectura

10. **`runPlanningPoker_shouldThrowPipelineException_onEmptyTeam`**
    - Pasa un `Team` con lista de miembros vacía
    - Verifica que se lanza `PipelineException`

11. **`runPlanningPoker_shouldThrowPipelineException_onNullTasksMarkdown`**
    - Pasa `null` como tasksMarkdown
    - Verifica que se lanza `PipelineException`

12. **`runPlanningPoker_shouldThrowPipelineException_onUnknownTaskId`**
    - Mockea el parser para devolver las tareas `["TASK-1"]`
    - Mockea el poker para devolver una tarea con id `"TASK-999"`
    - Verifica que se lanza `PipelineException`

13. **`runPlanningPoker_shouldHandleMarkdownFencedResponse`**
    - Mockea el LLM del planning poker para devolver JSON envuelto en ```json fences
    - Verifica que `JsonParser` elimina los fences y el resultado se parsea correctamente

14. **`runPlanningPoker_shouldLogWarningOnTotalPointsMismatch`**
    - Mockea el poker para devolver `totalPoints: 100` pero la suma de storyPoints es 50
    - Verifica que el resultado se devuelve igualmente (con warning registrado)

---

## Criterios de aceptación

1. `mvn clean compile` → SUCCESS
2. `mvn test` → 14 tests nuevos pasan (PlanningPokerOrchestratorTest)
3. Total de tests ≥ 75 pasando
4. `runPlanningPoker()` NO tiene tareas ni estimaciones hardcodeadas
5. Las tareas se parsean desde Markdown mediante LLM (Fase 4A)
6. Las estimaciones se generan mediante Planning Poker con LLM (Fase 4B)
7. Cada ID de tarea en la respuesta del LLM debe corresponder a una tarea parseada
8. Los nombres, roles y seniority de los miembros del equipo aparecen en el prompt del LLM
9. Los detalles de la arquitectura aparecen en el prompt del LLM
10. `totalPoints` validado de forma cruzada con la suma calculada (warning si no coincide)
11. Las entradas nulas o vacías lanzan `PipelineException`
12. La RuntimeException de `JsonParser` se envuelve en `PipelineException`
13. Los system prompts definidos como constantes `private static final String`

---

## Archivos a modificar

1. **`src/main/java/com/ideapipeline/orchestrator/PlanningPokerOrchestrator.java`**
   - Inyectar `LlmClient` mediante constructor
   - Reemplazar el placeholder hardcodeado con:
     - Método privado `parseTasksFromMarkdown()` (Fase 4A)
     - Estimación mediante Planning Poker con LLM (Fase 4B)
   - Añadir la validación cruzada de `totalPoints`

2. **`src/test/java/com/ideapipeline/orchestrator/PlanningPokerOrchestratorTest.java`**
   - Crear un archivo de test nuevo (o reemplazar el existente si hay uno)
   - 14 métodos de test según lo especificado arriba

---

## Criterios de éxito

Después de la implementación:
- ✅ El código compila sin warnings
- ✅ Los 14 tests pasan
- ✅ No quedan tareas ni estimaciones hardcodeadas
- ✅ Las tareas se parsean desde el contenido Markdown real
- ✅ Las estimaciones incluyen votos por miembro y desglose por rol
- ✅ Manejo de excepciones adecuado en todo el flujo
- ✅ Logging en los niveles apropiados

---

## Notas

- El helper `parseTasksFromMarkdown()` existe porque `tasks.md` es Markdown
  de forma libre proveniente de la fase de Síntesis. Parsearlo programáticamente
  sin un LLM sería frágil dada la variedad de estructuras Markdown
  que el LLM de síntesis podría producir.
- El mapa `adjacency` en `TaskGraph` representa un DAG (grafo dirigido acíclico).
  El LLM no debería producir ciclos.
- `pointsByRole` usa valores del enum `BaseRole` (por ejemplo, `BACKEND_DEV`, `FRONTEND_DEV`,
  `QA_ENGINEER`, `DEVOPS`, `FULLSTACK_DEV`, `MOBILE_DEV`).

---

Fin de phase-4-planning-poker.prompt.md v1.0
