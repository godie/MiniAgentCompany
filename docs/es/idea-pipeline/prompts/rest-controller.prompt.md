# rest-controller.prompt.md v1.1

## Rol
Eres un Desarrollador Java Senior que implementa la capa REST API del sistema idea-pipeline. Esto incluye una entidad JPA para el seguimiento de trabajos, un repositorio Spring Data, un servicio de ejecución asíncrona y un controlador REST con cinco endpoints.

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
- `ObjectMapper` — usa Jackson (ya está en el classpath vía Spring Boot)
- Dependencias ya en el classpath: Spring Web, Spring Data JPA, H2 (test), Jackson

## Estado del proyecto
- mvn clean compile → SUCCESS
- mvn test → 106/106 pasando

---

## Tu tarea

Implementa **5 archivos**:

1. `PipelineJobStatus.java` — enum
2. `PipelineJob.java` — entidad JPA
3. `PipelineJobRepository.java` — repositorio Spring Data
4. `PipelineJobService.java` — servicio de ejecución asíncrona
5. `PipelineController.java` — controlador REST

Más los DTOs en `com.ideapipeline.controller.dto`:
- `QuestionsRequest.java`
- `QuestionsResponse.java`
- `RunPipelineRequest.java`
- `RunPipelineResponse.java`
- `JobStatusResponse.java`
- `JobResultResponse.java`

---

## 1. PipelineJobStatus.java

**Paquete:** `com.ideapipeline.model.enums`

```java
public enum PipelineJobStatus {
    QUEUED, PROCESSING, DONE, ERROR
}
```

---

## 2. PipelineJob.java

**Paquete:** `com.ideapipeline.model`
**Anotaciones:** `@Entity`, `@Table(name = "pipeline_jobs")`, `@Slf4j`

**Campos:**

| Campo | Tipo | JPA | Notas |
|-------|------|-----|-------|
| `id` | `String` | `@Id` | UUID generado antes de persistir |
| `status` | `PipelineJobStatus` | `@Enumerated(EnumType.STRING)` | Por defecto: `QUEUED` |
| `createdAt` | `LocalDateTime` | `@Column(nullable=false)` | Se establece al crear |
| `updatedAt` | `LocalDateTime` | `@Column(nullable=false)` | Se actualiza en cada cambio de estado |
| `errorMessage` | `String` | `@Column(length=2000)` | Null salvo que el estado sea ERROR |
| `resultJson` | `String` | `@Column(columnDefinition="TEXT")` | Null hasta que el estado sea DONE |

**Métodos:**
- `static PipelineJob create()` — factoría: id UUID, status=QUEUED, createdAt=updatedAt=now
- `void markProcessing()` — status=PROCESSING, updatedAt=now
- `void markDone(String resultJson)` — status=DONE, resultJson, updatedAt=now
- `void markError(String errorMessage)` — status=ERROR, truncar el mensaje a 2000 caracteres, updatedAt=now

Constructor sin argumentos requerido por JPA. Usa `@Getter` + `@Setter` — nunca `@Data`.

---

## 3. PipelineJobRepository.java

**Paquete:** `com.ideapipeline.repository`

```java
public interface PipelineJobRepository extends JpaRepository<PipelineJob, String> {
}
```

---

## 4. PipelineJobService.java

**Paquete:** `com.ideapipeline.pipeline`
**Anotaciones:** `@Service`, `@Slf4j`
**Inyectar por constructor:** `IdeaPipeline`, `PipelineJobRepository`, `ObjectMapper`

### Método: `submitJob()`
```java
public String submitJob(RawIdea rawIdea, List<RefinementQA> refinements, Team team)
```
1. `PipelineJob job = PipelineJob.create()`
2. `repository.save(job)`
3. `log.info("Job {} submitted", job.getId())`
4. Llamar a `executeAsync(job.getId(), rawIdea, refinements, team)`
5. Devolver `job.getId()`

### Método: `executeAsync()`
```java
@Async
public void executeAsync(String jobId, RawIdea rawIdea, List<RefinementQA> refinements, Team team)
```
1. Cargar el job: `repository.findById(jobId).orElseThrow()`
2. `job.markProcessing()`, guardar
3. `log.info("Job {} processing", jobId)`
4. Try:
   - `ideaPipeline.run(rawIdea, refinements, team)` → `PipelineResult`
   - `objectMapper.writeValueAsString(result)` → `resultJson`
   - `job.markDone(resultJson)`, guardar
   - `log.info("Job {} done", jobId)`
5. Catch `Exception`:
   - `log.error("Job {} failed: {}", jobId, e.getMessage())`
   - `job.markError(e.getMessage())`, guardar

**Añade `@EnableAsync` al `PipelineConfig.java` existente — no crees una clase de configuración nueva.**

---

## 5. DTOs

**Paquete:** `com.ideapipeline.controller.dto`

### QuestionsRequest.java
```java
public record QuestionsRequest(String description) {}
```

### QuestionsResponse.java
```java
public record QuestionsResponse(List<String> questions) {}
```

### RunPipelineRequest.java
```java
public record RunPipelineRequest(
    String idea,
    List<RefinementQA> refinements,
    Team team
) {}
```

### RunPipelineResponse.java
```java
public record RunPipelineResponse(
    String jobId,
    String status,
    String message
) {}
```

### JobStatusResponse.java
```java
public record JobStatusResponse(
    String jobId,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String errorMessage
) {}
```

### JobResultResponse.java
```java
public record JobResultResponse(
    String jobId,
    String status,
    PipelineResult result
) {}
```

---

## 6. PipelineController.java

**Paquete:** `com.ideapipeline.controller`
**Anotaciones:** `@RestController`, `@RequestMapping("/pipeline")`, `@Slf4j`
**Inyectar por constructor:** `PipelineJobService`, `PipelineJobRepository`, `GatekeeperOrchestrator`, `ObjectMapper`

### Endpoints

#### POST /pipeline/questions
```
Request body: QuestionsRequest
Response 200: QuestionsResponse
Response 400: if description is null or blank
```

**Implementación:**
1. Si `request.description()` es null o está en blanco → `ResponseEntity.badRequest().build()`
2. Llamar a `gatekeeperOrchestrator.generateQuestions(new RawIdea(request.description()))` → `List<String>`
3. Devolver `ResponseEntity.ok(new QuestionsResponse(questions))`

**Propósito:** Expone la Fase 0 (Gatekeeper) para que el cliente pueda obtener las preguntas de refinamiento antes de llamar a `/pipeline/run`. El flujo de dos pasos previsto es:
1. `POST /pipeline/questions` → obtener preguntas
2. Responder las preguntas localmente
3. `POST /pipeline/run` → enviar con la lista de `RefinementQA`

#### POST /pipeline/run
```
Request body: RunPipelineRequest
Response 202: RunPipelineResponse(jobId, "QUEUED", "Pipeline job submitted")
Response 400: if idea is null/blank, team is null, or team.members() is null/empty
```

**Implementación:**
1. Si `request.idea()` es null o está en blanco → `ResponseEntity.badRequest().build()`
2. Si `request.team()` es null → `ResponseEntity.badRequest().build()`
3. Si `request.team().members()` es null o está vacío → `ResponseEntity.badRequest().build()`
4. Construir `RawIdea`, resolver refinements (null → lista vacía)
5. Llamar a `pipelineJobService.submitJob(rawIdea, refinements, team)` → `jobId`
6. Devolver `ResponseEntity.accepted().body(new RunPipelineResponse(jobId, "QUEUED", "Pipeline job submitted"))`

#### GET /pipeline/{jobId}/status
```
Response 200: JobStatusResponse
Response 404: if jobId not found
```

**Implementación:**
1. `repository.findById(jobId)` → si está vacío, `ResponseEntity.notFound().build()`
2. Devolver `ResponseEntity.ok(new JobStatusResponse(job.getId(), job.getStatus().name(), job.getCreatedAt(), job.getUpdatedAt(), job.getErrorMessage()))`

#### GET /pipeline/{jobId}/result
```
Response 200: JobResultResponse with deserialized PipelineResult
Response 404: if jobId not found
Response 409: if job status is not DONE
```

**Implementación:**
1. `repository.findById(jobId)` → si está vacío, `ResponseEntity.notFound().build()`
2. Si `job.getStatus() != DONE` → `ResponseEntity.status(HttpStatus.CONFLICT).build()`
3. `objectMapper.readValue(job.getResultJson(), PipelineResult.class)` → `PipelineResult`
4. Devolver `ResponseEntity.ok(new JobResultResponse(job.getId(), job.getStatus().name(), result))`
5. Si la deserialización falla → `ResponseEntity.internalServerError().build()`

#### GET /pipeline/health
```
Response 200: "OK"
```

---

## Logging
- `PipelineController`: `log.info()` en cada petición con el nombre del endpoint
- `PipelineJobService`: `log.info()` al enviar/procesar/completar, `log.error()` en caso de fallo

---

## Estándares de código
- Inyección solo por constructor — sin `@Autowired` en campos
- `@EnableAsync` añadido al `PipelineConfig.java` existente
- Sin `@Data` en entidades JPA — usa `@Getter` + `@Setter`
- Todo el JSON mediante el `ObjectMapper` inyectado
- `PipelineJob.create()` — nunca `new PipelineJob()` fuera de la entidad
- `refinements` null → lista vacía, nunca lanzar

---

## Requisitos de pruebas (TDD)

**Archivo de test:** `src/test/java/com/ideapipeline/controller/PipelineControllerTest.java`

Usa `@WebMvcTest(PipelineController.class)` + `@MockBean` para `PipelineJobService`, `PipelineJobRepository`, `GatekeeperOrchestrator`, `ObjectMapper`.

### Tests requeridos

- `postQuestions_shouldReturn200WithQuestions`
  POST `/pipeline/questions` con `{"description": "My idea"}`.
  Mockea `gatekeeperOrchestrator.generateQuestions()` para que devuelva `["Q1?", "Q2?"]`.
  Verifica estado 200, cuerpo contiene `questions = ["Q1?", "Q2?"]`.

- `postQuestions_shouldReturn400WhenDescriptionIsBlank`
  POST `/pipeline/questions` con `{"description": ""}`.
  Verifica estado 400.

- `postRun_shouldReturn202WithJobId`
  POST `/pipeline/run` con un cuerpo válido.
  Mockea `pipelineJobService.submitJob()` para que devuelva `"job-123"`.
  Verifica estado 202, cuerpo contiene `jobId = "job-123"`, `status = "QUEUED"`.

- `postRun_shouldReturn400WhenIdeaIsBlank`
  Verifica estado 400.

- `postRun_shouldReturn400WhenTeamIsNull`
  Verifica estado 400.

- `postRun_shouldReturn400WhenTeamMembersIsEmpty`
  POST con `team = {"members": []}`.
  Verifica estado 400.

- `postRun_shouldTreatNullRefinementsAsEmptyList`
  POST con `refinements = null`.
  Verifica que `submitJob()` se llama con una lista vacía, estado 202.

- `getStatus_shouldReturn200WithJobStatus`
  Mockea un job con estado PROCESSING.
  Verifica estado 200, cuerpo contiene `status = "PROCESSING"`.

- `getStatus_shouldReturn404WhenJobNotFound`
  Verifica estado 404.

- `getResult_shouldReturn200WithDeserializedResult`
  Mockea un job con estado DONE y `resultJson` válido.
  Mockea `objectMapper.readValue()` para que devuelva un `PipelineResult` válido.
  Verifica estado 200.

- `getResult_shouldReturn409WhenJobNotDone`
  Mockea un job con estado PROCESSING.
  Verifica estado 409.

- `getResult_shouldReturn404WhenJobNotFound`
  Verifica estado 404.

- `getHealth_shouldReturn200OK`
  Verifica estado 200, cuerpo = `"OK"`.

**Archivo de test:** `src/test/java/com/ideapipeline/pipeline/PipelineJobServiceTest.java`

### Tests requeridos

- `submitJob_shouldSaveJobAndReturnJobId`
  Verifica que se llama `repository.save()`, el id devuelto no es null.

- `submitJob_shouldCallExecuteAsync`
  Verifica que `executeAsync()` se invoca después de guardar.

- `executeAsync_shouldMarkProcessingThenDone`
  Mockea `ideaPipeline.run()` → `PipelineResult` válido.
  Mockea `objectMapper.writeValueAsString()` → `"{}"`.
  Llama a `executeAsync()` directamente.
  Verifica que save se llama con estado PROCESSING y luego DONE.

- `executeAsync_shouldMarkErrorOnException`
  Mockea `ideaPipeline.run()` para que lance `PipelineException("failed", "Phase1", 0)`.
  Verifica que save se llama con estado ERROR, `errorMessage` no es null.

- `executeAsync_shouldTruncateErrorMessageOver2000Chars`
  Mockea una excepción con un mensaje de 2500 caracteres.
  Verifica `job.getErrorMessage().length() <= 2000`.

---

## Criterios de aceptación
1. `mvn clean compile` → SUCCESS
2. `mvn test` → 18 tests nuevos pasan (13 del controlador + 5 del servicio)
3. `mvn test` → 124 tests en total pasando
4. `POST /pipeline/questions` expone el Gatekeeper — el cliente puede obtener preguntas antes de enviar
5. `POST /pipeline/run` devuelve 202 inmediatamente — no bloquea
6. `GET /pipeline/{jobId}/status` devuelve el estado actual del job
7. `GET /pipeline/{jobId}/result` devuelve 409 si no está DONE, 200 con el resultado si está DONE
8. `GET /pipeline/health` devuelve 200 "OK"
9. `@EnableAsync` añadido a `PipelineConfig.java` — no una clase nueva
10. Mensaje de error truncado a 2000 caracteres en `markError()`
11. `refinements` null tratado como lista vacía
12. Sin `@Data` en `PipelineJob`
13. `team.members()` vacío → 400

---
