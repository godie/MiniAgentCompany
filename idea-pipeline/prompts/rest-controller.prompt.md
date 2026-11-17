# rest-controller.prompt.md v1.1

## Role
You are a Senior Java Developer implementing the REST API layer of the idea-pipeline system. This includes a JPA entity for job tracking, a Spring Data repository, an async execution service, and a REST controller with five endpoints.

## Context
Read these files before writing any code:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

---

## What already exists — do not modify

- `IdeaPipeline.java` — fully implemented. Method: `run(RawIdea, List<RefinementQA>, Team)`
- `GatekeeperOrchestrator.java` — fully implemented. Method: `generateQuestions(RawIdea)` → `List<String>`
- All records — do not modify:
  - `RawIdea(String description)`
  - `RefinementQA(String question, String answer)`
  - `Team(List<TeamMember> members)`
  - `TeamMember(String id, String name, BaseRole baseRole, Seniority seniority, List<String> extraResponsibilities)`
  - `PipelineResult(IdeaContext context, PipelineOutput documents, ArchitectureDoc architecture, TaskGraph taskGraph, int loopsRequired)`
- Enums — do not modify:
  - `BaseRole`: FRONTEND_DEV, BACKEND_DEV, FULLSTACK_DEV, QA_ENGINEER, DEVOPS, MOBILE_DEV
  - `Seniority`: JUNIOR, MID, SENIOR, LEAD, PRINCIPAL
- `PipelineException.java` — already exists
- `ObjectMapper` — use Jackson (already on classpath via Spring Boot)
- Dependencies already on classpath: Spring Web, Spring Data JPA, H2 (test), Jackson

## Project status
- mvn clean compile → SUCCESS
- mvn test → 106/106 passing

---

## Your task

Implement **5 files**:

1. `PipelineJobStatus.java` — enum
2. `PipelineJob.java` — JPA entity
3. `PipelineJobRepository.java` — Spring Data repository
4. `PipelineJobService.java` — async execution service
5. `PipelineController.java` — REST controller

Plus DTOs in `com.ideapipeline.controller.dto`:
- `QuestionsRequest.java`
- `QuestionsResponse.java`
- `RunPipelineRequest.java`
- `RunPipelineResponse.java`
- `JobStatusResponse.java`
- `JobResultResponse.java`

---

## 1. PipelineJobStatus.java

**Package:** `com.ideapipeline.model.enums`

```java
public enum PipelineJobStatus {
    QUEUED, PROCESSING, DONE, ERROR
}
```

---

## 2. PipelineJob.java

**Package:** `com.ideapipeline.model`
**Annotations:** `@Entity`, `@Table(name = "pipeline_jobs")`, `@Slf4j`

**Fields:**

| Field | Type | JPA | Notes |
|-------|------|-----|-------|
| `id` | `String` | `@Id` | UUID generated before persist |
| `status` | `PipelineJobStatus` | `@Enumerated(EnumType.STRING)` | Default: `QUEUED` |
| `createdAt` | `LocalDateTime` | `@Column(nullable=false)` | Set on creation |
| `updatedAt` | `LocalDateTime` | `@Column(nullable=false)` | Updated on every status change |
| `errorMessage` | `String` | `@Column(length=2000)` | Null unless status is ERROR |
| `resultJson` | `String` | `@Column(columnDefinition="TEXT")` | Null until status is DONE |

**Methods:**
- `static PipelineJob create()` — factory: UUID id, status=QUEUED, createdAt=updatedAt=now
- `void markProcessing()` — status=PROCESSING, updatedAt=now
- `void markDone(String resultJson)` — status=DONE, resultJson, updatedAt=now
- `void markError(String errorMessage)` — status=ERROR, truncate message to 2000 chars, updatedAt=now

No-arg constructor required by JPA. Use `@Getter` + `@Setter` — never `@Data`.

---

## 3. PipelineJobRepository.java

**Package:** `com.ideapipeline.repository`

```java
public interface PipelineJobRepository extends JpaRepository<PipelineJob, String> {
}
```

---

## 4. PipelineJobService.java

**Package:** `com.ideapipeline.pipeline`
**Annotations:** `@Service`, `@Slf4j`
**Inject via constructor:** `IdeaPipeline`, `PipelineJobRepository`, `ObjectMapper`

### Method: `submitJob()`
```java
public String submitJob(RawIdea rawIdea, List<RefinementQA> refinements, Team team)
```
1. `PipelineJob job = PipelineJob.create()`
2. `repository.save(job)`
3. `log.info("Job {} submitted", job.getId())`
4. Call `executeAsync(job.getId(), rawIdea, refinements, team)`
5. Return `job.getId()`

### Method: `executeAsync()`
```java
@Async
public void executeAsync(String jobId, RawIdea rawIdea, List<RefinementQA> refinements, Team team)
```
1. Load job: `repository.findById(jobId).orElseThrow()`
2. `job.markProcessing()`, save
3. `log.info("Job {} processing", jobId)`
4. Try:
   - `ideaPipeline.run(rawIdea, refinements, team)` → `PipelineResult`
   - `objectMapper.writeValueAsString(result)` → `resultJson`
   - `job.markDone(resultJson)`, save
   - `log.info("Job {} done", jobId)`
5. Catch `Exception`:
   - `log.error("Job {} failed: {}", jobId, e.getMessage())`
   - `job.markError(e.getMessage())`, save

**Add `@EnableAsync` to existing `PipelineConfig.java` — do not create a new config class.**

---

## 5. DTOs

**Package:** `com.ideapipeline.controller.dto`

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

**Package:** `com.ideapipeline.controller`
**Annotations:** `@RestController`, `@RequestMapping("/pipeline")`, `@Slf4j`
**Inject via constructor:** `PipelineJobService`, `PipelineJobRepository`, `GatekeeperOrchestrator`, `ObjectMapper`

### Endpoints

#### POST /pipeline/questions
```
Request body: QuestionsRequest
Response 200: QuestionsResponse
Response 400: if description is null or blank
```

**Implementation:**
1. If `request.description()` is null or blank → `ResponseEntity.badRequest().build()`
2. Call `gatekeeperOrchestrator.generateQuestions(new RawIdea(request.description()))` → `List<String>`
3. Return `ResponseEntity.ok(new QuestionsResponse(questions))`

**Purpose:** Exposes Phase 0 (Gatekeeper) so the client can obtain refinement questions before calling `/pipeline/run`. The intended two-step flow is:
1. `POST /pipeline/questions` → get questions
2. Answer questions locally
3. `POST /pipeline/run` → submit with `RefinementQA` list

#### POST /pipeline/run
```
Request body: RunPipelineRequest
Response 202: RunPipelineResponse(jobId, "QUEUED", "Pipeline job submitted")
Response 400: if idea is null/blank, team is null, or team.members() is null/empty
```

**Implementation:**
1. If `request.idea()` is null or blank → `ResponseEntity.badRequest().build()`
2. If `request.team()` is null → `ResponseEntity.badRequest().build()`
3. If `request.team().members()` is null or empty → `ResponseEntity.badRequest().build()`
4. Build `RawIdea`, resolve refinements (null → empty list)
5. Call `pipelineJobService.submitJob(rawIdea, refinements, team)` → `jobId`
6. Return `ResponseEntity.accepted().body(new RunPipelineResponse(jobId, "QUEUED", "Pipeline job submitted"))`

#### GET /pipeline/{jobId}/status
```
Response 200: JobStatusResponse
Response 404: if jobId not found
```

**Implementation:**
1. `repository.findById(jobId)` → if empty, `ResponseEntity.notFound().build()`
2. Return `ResponseEntity.ok(new JobStatusResponse(job.getId(), job.getStatus().name(), job.getCreatedAt(), job.getUpdatedAt(), job.getErrorMessage()))`

#### GET /pipeline/{jobId}/result
```
Response 200: JobResultResponse with deserialized PipelineResult
Response 404: if jobId not found
Response 409: if job status is not DONE
```

**Implementation:**
1. `repository.findById(jobId)` → if empty, `ResponseEntity.notFound().build()`
2. If `job.getStatus() != DONE` → `ResponseEntity.status(HttpStatus.CONFLICT).build()`
3. `objectMapper.readValue(job.getResultJson(), PipelineResult.class)` → `PipelineResult`
4. Return `ResponseEntity.ok(new JobResultResponse(job.getId(), job.getStatus().name(), result))`
5. If deserialization fails → `ResponseEntity.internalServerError().build()`

#### GET /pipeline/health
```
Response 200: "OK"
```

---

## Logging
- `PipelineController`: `log.info()` on each request with endpoint name
- `PipelineJobService`: `log.info()` on submit/processing/done, `log.error()` on failure

---

## Coding standards
- Constructor injection only — no `@Autowired` on fields
- `@EnableAsync` added to existing `PipelineConfig.java`
- No `@Data` on JPA entities — use `@Getter` + `@Setter`
- All JSON via injected `ObjectMapper`
- `PipelineJob.create()` — never `new PipelineJob()` outside the entity
- Null `refinements` → empty list, never throw

---

## Testing requirements (TDD)

**Test file:** `src/test/java/com/ideapipeline/controller/PipelineControllerTest.java`

Use `@WebMvcTest(PipelineController.class)` + `@MockBean` for `PipelineJobService`, `PipelineJobRepository`, `GatekeeperOrchestrator`, `ObjectMapper`.

### Tests required

- `postQuestions_shouldReturn200WithQuestions`
  POST `/pipeline/questions` with `{"description": "My idea"}`.
  Mock `gatekeeperOrchestrator.generateQuestions()` to return `["Q1?", "Q2?"]`.
  Verify status 200, body contains `questions = ["Q1?", "Q2?"]`.

- `postQuestions_shouldReturn400WhenDescriptionIsBlank`
  POST `/pipeline/questions` with `{"description": ""}`.
  Verify status 400.

- `postRun_shouldReturn202WithJobId`
  POST `/pipeline/run` with valid body.
  Mock `pipelineJobService.submitJob()` to return `"job-123"`.
  Verify status 202, body contains `jobId = "job-123"`, `status = "QUEUED"`.

- `postRun_shouldReturn400WhenIdeaIsBlank`
  Verify status 400.

- `postRun_shouldReturn400WhenTeamIsNull`
  Verify status 400.

- `postRun_shouldReturn400WhenTeamMembersIsEmpty`
  POST with `team = {"members": []}`.
  Verify status 400.

- `postRun_shouldTreatNullRefinementsAsEmptyList`
  POST with `refinements = null`.
  Verify `submitJob()` called with empty list, status 202.

- `getStatus_shouldReturn200WithJobStatus`
  Mock job with status PROCESSING.
  Verify status 200, body contains `status = "PROCESSING"`.

- `getStatus_shouldReturn404WhenJobNotFound`
  Verify status 404.

- `getResult_shouldReturn200WithDeserializedResult`
  Mock job with status DONE and valid `resultJson`.
  Mock `objectMapper.readValue()` to return valid `PipelineResult`.
  Verify status 200.

- `getResult_shouldReturn409WhenJobNotDone`
  Mock job with status PROCESSING.
  Verify status 409.

- `getResult_shouldReturn404WhenJobNotFound`
  Verify status 404.

- `getHealth_shouldReturn200OK`
  Verify status 200, body = `"OK"`.

**Test file:** `src/test/java/com/ideapipeline/pipeline/PipelineJobServiceTest.java`

### Tests required

- `submitJob_shouldSaveJobAndReturnJobId`
  Verify `repository.save()` called, returned id not null.

- `submitJob_shouldCallExecuteAsync`
  Verify `executeAsync()` invoked after save.

- `executeAsync_shouldMarkProcessingThenDone`
  Mock `ideaPipeline.run()` → valid `PipelineResult`.
  Mock `objectMapper.writeValueAsString()` → `"{}"`.
  Call `executeAsync()` directly.
  Verify save called with PROCESSING then DONE status.

- `executeAsync_shouldMarkErrorOnException`
  Mock `ideaPipeline.run()` to throw `PipelineException("failed", "Phase1", 0)`.
  Verify save called with ERROR status, `errorMessage` not null.

- `executeAsync_shouldTruncateErrorMessageOver2000Chars`
  Mock exception with 2500-char message.
  Verify `job.getErrorMessage().length() <= 2000`.

---

## Acceptance criteria
1. `mvn clean compile` → SUCCESS
2. `mvn test` → 18 new tests pass (13 controller + 5 service)
3. `mvn test` → 124 total tests passing
4. `POST /pipeline/questions` exposes Gatekeeper — client can get questions before submitting
5. `POST /pipeline/run` returns 202 immediately — does not block
6. `GET /pipeline/{jobId}/status` returns current job state
7. `GET /pipeline/{jobId}/result` returns 409 if not DONE, 200 with result if DONE
8. `GET /pipeline/health` returns 200 "OK"
9. `@EnableAsync` added to `PipelineConfig.java` — not a new class
10. Error message truncated to 2000 chars in `markError()`
11. Null `refinements` treated as empty list
12. No `@Data` on `PipelineJob`
13. `team.members()` empty → 400

---
