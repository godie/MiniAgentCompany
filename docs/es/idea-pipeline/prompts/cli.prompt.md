## Validaciones de entrada (capa CLI)

- `--idea` vacío: validar con `ideaText.isBlank()` → `exitWithError("Error: --idea cannot be empty")`
- Directorio padre de `--output`: llamar `Files.createDirectories()` antes de escribir — ignorar si ya existe, `exitWithError` si falla
- `team.members()` vacío: en `parseTeam()`, después de deserializar → `exitWithError("Error: team must have at least one member")`
- Respuesta vacía en interactivo: aceptar pero imprimir `"Warning: empty answer for question {i}"` a stderr — no bloquear

## Rol
Eres un Desarrollador Java Senior que implementa el punto de entrada CLI del sistema idea-pipeline.
La CLI permite ejecutar el pipeline completo desde la terminal, con dos modos: ejecución directa mediante flags + archivos JSON, y un modo interactivo en el que el Gatekeeper genera las preguntas de refinamiento y el usuario las responde en la terminal.

## Contexto
Lee estos archivos antes de escribir cualquier código:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

---

## Lo que ya existe — no modificar

- `IdeaPipeline.java` — completamente implementado. Método: `run(RawIdea, List<RefinementQA>, Team)`
- `GatekeeperOrchestrator.java` — completamente implementado. Método: `generateQuestions(RawIdea)` → `List<String>`
- Todos los records — no modificar:
  - `RawIdea(String description)`
  - `RefinementQA(String question, String answer)`
  - `Team(List<TeamMember> members)`
  - `TeamMember(String id, String name, BaseRole baseRole, Seniority seniority, List<String> extraResponsibilities)`
  - `PipelineResult(IdeaContext context, PipelineOutput documents, ArchitectureDoc architecture, TaskGraph taskGraph, int loopsRequired)`
- Enums — no modificar:
  - `BaseRole`: FRONTEND_DEV, BACKEND_DEV, FULLSTACK_DEV, QA_ENGINEER, DEVOPS, MOBILE_DEV
  - `Seniority`: JUNIOR, MID, SENIOR, LEAD, PRINCIPAL
- `PipelineException.java` — ya existe
- `ObjectMapper` — usar Jackson (ya está en el classpath vía Spring Boot)
- `PipelineRunner.java` — TU ARCHIVO OBJETIVO.
  Anotar con `@Component`. Implementa `ApplicationRunner`.
  Inyectar `IdeaPipeline`, `GatekeeperOrchestrator`, `ObjectMapper` por constructor.

## Estado del proyecto
- mvn clean compile → SUCCESS
- mvn test → 96/96 pasando

---

## Tu tarea

Implementar `PipelineRunner.java` — un método: `run(ApplicationArguments args)`

---

## Interfaz CLI

### Flags

| Flag | Obligatorio | Descripción |
|------|----------|-------------|
| `--idea` | Sí | Texto de la idea en bruto como string |
| `--team` | Sí | Ruta a un archivo JSON que representa el equipo |
| `--interactive` | No | Si está presente, activa el modo interactivo para los refinamientos |
| `--refinements` | No (ignorado si `--interactive`) | Ruta a un archivo JSON con refinamientos preescritos |
| `--output` | No | Ruta donde escribir el JSON del `PipelineResult`. Por defecto: `pipeline-result.json` en el directorio actual |

### Formato de team.json
```json
{
  "members": [
    {
      "id": "tm-1",
      "name": "Alice",
      "baseRole": "BACKEND_DEV",
      "seniority": "SENIOR",
      "extraResponsibilities": []
    }
  ]
}
```

### Formato de refinements.json (se usa cuando `--interactive` NO está presente)
```json
[
  { "question": "Who is the target user?", "answer": "Developers" },
  { "question": "What is the main constraint?", "answer": "Must be offline-first" }
]
```

---

## Modos de ejecución

### Modo A — Directo (sin flag `--interactive`)

1. Leer `--idea` → `String ideaText`
2. Leer `--team` → parsear `team.json` → `Team`
3. Leer `--refinements` → parsear `refinements.json` → `List<RefinementQA>`. Si el flag no está, usar `Collections.emptyList()`
4. Ejecutar el pipeline: `ideaPipeline.run(new RawIdea(ideaText), refinements, team)`
5. Escribir el resultado en stdout + archivo de salida

### Modo B — Interactivo (flag `--interactive` presente)

1. Leer `--idea` → `String ideaText`
2. Leer `--team` → parsear `team.json` → `Team`
3. Llamar `gatekeeperOrchestrator.generateQuestions(new RawIdea(ideaText))` → `List<String> questions`
4. Para cada pregunta, imprimir en stdout y leer la respuesta del usuario desde `System.in`:
   ```
   [Question 1/5] Who is the target user?
   > _
   ```
5. Construir `List<RefinementQA>` a partir de preguntas + respuestas
6. Ejecutar el pipeline: `ideaPipeline.run(new RawIdea(ideaText), refinements, team)`
7. Escribir el resultado en stdout + archivo de salida

---

## Salida

### Resumen en stdout (impreso al completarse el pipeline)
```
=== PIPELINE COMPLETE ===
Loops required : {loopsRequired}
Total points   : {taskGraph.totalPoints()}
Tasks estimated: {taskGraph.tasks().size()}
Stack          : {architecture.stack()}
Output file    : {outputPath}
```

### Archivo de salida
Escribir el `PipelineResult` completo como JSON con formato legible en la ruta especificada por `--output` (por defecto: `pipeline-result.json`).
Usar `objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result)`.

---

## Manejo de errores

- Si falta `--idea`: imprimir `"Error: --idea flag is required"` en stderr y salir con código 1
- Si falta `--team`: imprimir `"Error: --team flag is required"` en stderr y salir con código 1
- Si el archivo de `--team` no existe o no se puede parsear: imprimir `"Error: cannot read team file: {path}"` en stderr y salir con código 1
- Si se especifica el archivo de `--refinements` pero no se puede parsear: imprimir `"Error: cannot read refinements file: {path}"` en stderr y salir con código 1
- Si se lanza `PipelineException` durante la ejecución: imprimir `"Pipeline error [{phase}]: {message}"` en stderr y salir con código 1
- Si se lanza cualquier otra excepción: imprimir `"Unexpected error: {message}"` en stderr y salir con código 1
- **Nunca lanzar excepciones fuera de `run()` — siempre capturar y salir con código 1**

Usar `System.exit(1)` para todas las salidas de error. Usar un helper privado `exitWithError(String message)` que imprime en stderr y llama a `System.exit(1)`.

---

## Helpers privados

- `parseTeam(String path)` → `Team`: lee el archivo en la ruta, deserializa el JSON usando `objectMapper.readValue()`
- `parseRefinements(String path)` → `List<RefinementQA>`: lee el archivo en la ruta, deserializa el array JSON
- `readAnswersFromConsole(List<String> questions)` → `List<RefinementQA>`: imprime cada pregunta con su índice, lee la respuesta vía `Scanner(System.in)`, devuelve la lista de `RefinementQA`
- `writeResult(PipelineResult result, String outputPath)`: serializa a JSON con formato legible y escribe en el archivo
- `printSummary(PipelineResult result, String outputPath)`: imprime el bloque de resumen en stdout
- `exitWithError(String message)`: imprime en `System.err` y llama a `System.exit(1)`

---

## Registro (logging)
- `log.info()` al entrar: modo (interactivo/directo) + longitud de la idea + ruta del archivo del equipo
- `log.info()` al completarse el pipeline: loopsRequired + totalPoints
- `log.error()` ante cualquier excepción capturada antes de salir

---

## Estándares de código
- Solo inyección por constructor
- `@Component` + `@Slf4j`
- Sin `@Autowired` en campos
- `Scanner` creado una vez para toda la sesión interactiva, cerrado después de responder todas las preguntas
- `ObjectMapper` inyectado — no instanciarlo manualmente
- Toda la E/S de archivos vía `java.nio.file.Files` o `objectMapper.readValue(new File(path), ...)`
- `System.exit()` solo dentro de `exitWithError()` — nunca llamarlo directamente en otro lugar

---

## Requisitos de pruebas (TDD)

> Nota: `PipelineRunner` usa `System.exit()` y `System.in`, lo que dificulta probar unitariamente el flujo completo. Las pruebas se centran en los métodos helper y en la lógica de invocación del pipeline usando mocks. `exitWithError()` y `System.exit()` no se prueban directamente.

Archivo de pruebas: `src/test/java/com/ideapipeline/cli/PipelineRunnerTest.java`

### Configuración
Mockear `IdeaPipeline`, `GatekeeperOrchestrator`, `ObjectMapper`.
Usar una instancia real de `ObjectMapper` para las pruebas de parseo JSON.

### Pruebas requeridas

- `run_shouldExecutePipelineInDirectMode`
  Pasar args: `--idea=My idea`, `--team=path/to/team.json` (usar un archivo temporal con JSON válido).
  Mockear `ideaPipeline.run()` para devolver un `PipelineResult` válido.
  Verificar que `ideaPipeline.run()` se llama una vez con el `RawIdea` correcto.

- `run_shouldExecutePipelineWithRefinementsFile`
  Pasar args: `--idea=My idea`, `--team=...`, `--refinements=path/to/refinements.json` (usar archivo temporal).
  Verificar que `ideaPipeline.run()` se llama con un `List<RefinementQA>` no vacío.

- `run_shouldUseEmptyRefinementsWhenFlagAbsent`
  Pasar args: `--idea=My idea`, `--team=...` (sin `--refinements`).
  Verificar que `ideaPipeline.run()` se llama con un `List<RefinementQA>` vacío.

- `run_shouldExecutePipelineInInteractiveMode`
  Pasar args: `--idea=My idea`, `--team=...`, `--interactive`.
  Mockear `gatekeeperOrchestrator.generateQuestions()` para devolver `["Q1?", "Q2?"]`.
  Simular `System.in` con las respuestas `"A1\nA2\n"`.
  Verificar que `ideaPipeline.run()` se llama con un `List<RefinementQA>` de tamaño 2 que contiene los pares Q+A correctos.

- `run_shouldIgnoreRefinementsFileInInteractiveMode`
  Pasar args: `--idea=My idea`, `--team=...`, `--interactive`, `--refinements=...`.
  Verificar que `gatekeeperOrchestrator.generateQuestions()` se llama y que `parseRefinements` NO se usa.

- `run_shouldWriteResultToDefaultOutputFile`
  Ejecutar en modo directo sin flag `--output`.
  Verificar que se crea un archivo llamado `pipeline-result.json` en el directorio actual.
  Limpiar después de la prueba.

- `run_shouldWriteResultToCustomOutputFile`
  Pasar `--output=/tmp/my-result.json`.
  Verificar que el archivo se crea en esa ruta.
  Limpiar después de la prueba.

- `parseTeam_shouldDeserializeValidJson`
  Escribir un `team.json` válido en un archivo temporal.
  Llamar `parseTeam()` directamente (hacerlo package-private para las pruebas).
  Verificar que el `Team` devuelto tiene los miembros correctos.

- `parseRefinements_shouldDeserializeValidJson`
  Escribir un `refinements.json` válido en un archivo temporal.
  Llamar `parseRefinements()` directamente.
  Verificar que la lista devuelta tiene las entradas `RefinementQA` correctas.

- `readAnswersFromConsole_shouldBuildRefinementQAList`
  Llamar `readAnswersFromConsole()` directamente con 2 preguntas.
  Simular la entrada `"Answer1\nAnswer2\n"` reemplazando `System.in`.
  Verificar que la lista devuelta tiene 2 `RefinementQA` con los pares pregunta/respuesta correctos.

---

## Criterios de aceptación
1. `mvn clean compile` → SUCCESS
2. `mvn test` → 10 pruebas nuevas pasan (PipelineRunnerTest)
3. `mvn test` → 106 pruebas en total pasando
4. El modo `--interactive` llama a `generateQuestions()` y lee las respuestas desde stdin
5. El flag `--refinements` se ignora cuando `--interactive` está presente
6. El archivo de salida por defecto es `pipeline-result.json` cuando no se especifica `--output`
7. Todos los errores se imprimen en stderr y salen con código 1 — nunca lanzar fuera de `run()`
8. `Scanner` se cierra después de la sesión interactiva
9. `ObjectMapper` se inyecta, nunca se instancia dentro de la clase
10. `System.exit()` solo se llama dentro de `exitWithError()`
