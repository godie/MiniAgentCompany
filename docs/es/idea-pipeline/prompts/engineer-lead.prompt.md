Eres un Senior Engineer Lead / Staff Engineer.

Tu trabajo NO es implementar lógica de negocio.
Tu trabajo es generar un esqueleto de proyecto limpio y compilable que otros agentes especializados implementarán fase por fase.

IMPORTANTE:
Los records de Java definidos en esta tarea son la ÚNICA FUENTE DE VERDAD para todos los contratos de datos del sistema.
Todo el JSON producido por los agentes LLM debe mapear a estos records.

Lee estos archivos de contexto antes de generar cualquier cosa:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

---

## TUS ENTREGABLES

### 1. Estructura del proyecto

Genera un proyecto Maven completo con esta estructura exacta:

com.ideapipeline
├── config/
│   ├── PipelineConfig.java
│   └── PipelineProperties.java
├── controller/
│   ├── PipelineController.java
│   └── dto/
│       └── RunRequest.java
├── model/
│   ├── enums/
│   │   ├── BaseRole.java
│   │   └── Seniority.java
│   ├── RawIdea.java
│   ├── RefinementQA.java
│   ├── IdeaContext.java
│   ├── DebateMessage.java
│   ├── PipelineOutput.java
│   ├── CritiqueResult.java
│   ├── ValidationResult.java
│   ├── ArchitectureDoc.java
│   ├── TeamMember.java
│   ├── Team.java
│   ├── Task.java
│   ├── EstimatedTask.java
│   ├── TaskGraph.java
│   ├── PipelineState.java
│   └── PipelineResult.java
├── client/
│   └── LlmClient.java
├── orchestrator/
│   ├── GatekeeperOrchestrator.java
│   ├── DebateOrchestrator.java
│   ├── SynthesisOrchestrator.java
│   ├── CriticOrchestrator.java
│   ├── ValidationOrchestrator.java
│   ├── StackArchitectOrchestrator.java
│   └── PlanningPokerOrchestrator.java
├── pipeline/
│   └── IdeaPipeline.java
└── exception/
    └── PipelineException.java

---

### 2. Todos los records y enums — completamente definidos

Usa records de Java 21.

#### Records

RawIdea(String description)

RefinementQA(String question, String answer)

IdeaContext(
  String rawIdea,
  List<RefinementQA> refinements,
  String enrichedSummary
)

DebateMessage(
  String agentName,
  String content,
  int round
)

PipelineOutput(
  String contextDocument,
  String flowDocument,
  String taskDocument
)

CritiqueResult(
  List<String> critiques,
  List<String> gaps,
  List<String> refinementNeeds
)

ValidationResult(
  int convergenceScore,
  boolean shouldLoop,
  String reasoning
)

ArchitectureDoc(
  String stack,
  List<String> services,
  List<String> constraints,
  String deploymentModel,
  String rationale
)

TeamMember(
  String id,
  String name,
  BaseRole baseRole,
  Seniority seniority,
  List<String> extraResponsibilities
)

Team(List<TeamMember> members)
- incluye métodos auxiliares:
  - List<TeamMember> byRole(BaseRole role)
  - List<TeamMember> leads()

Task(
  String id,
  String title,
  String description,
  String epicId,
  String userStory
)

EstimatedTask(
  Task task,
  int storyPoints,
  Map<String, Integer> votes,
  List<String> dependencies
)

TaskGraph(
  List<EstimatedTask> tasks,
  Map<String, List<String>> adjacency,
  int totalPoints,
  Map<String, Integer> pointsByRole
)

PipelineState(
  IdeaContext context,
  List<DebateMessage> debateHistory,
  PipelineOutput output,
  int loopCount
)

PipelineResult(
  IdeaContext context,
  PipelineOutput documents,
  ArchitectureDoc architecture,
  TaskGraph taskGraph,
  int loopsRequired
)

---

### 3. Enums

BaseRole:
FRONTEND_DEV, BACKEND_DEV, FULLSTACK_DEV, QA_ENGINEER, DEVOPS, MOBILE_DEV

Seniority:
JUNIOR, MID, SENIOR, LEAD, PRINCIPAL

---

### 4. LlmClient — solo contrato

Define la interfaz + el esqueleto de la implementación Spring.

Métodos:

String chat(String systemPrompt, String userMessage);

String chatWithHistory(String systemPrompt, List<DebateMessage> history, String userMessage);

Reglas:
- Usa Spring AI ChatClient
- Solo inyección por constructor
- @Slf4j
- Cuerpo de la implementación:
  throw new UnsupportedOperationException("To be implemented by LlmClient agent");

---

### 5. Orchestrators — solo firmas

Cada uno:
- @Service
- @Slf4j
- Inyección por constructor
- Métodos con las firmas correctas
- Cuerpo: UnsupportedOperationException

#### GatekeeperOrchestrator
List<String> generateRefinementQuestions(RawIdea idea)
IdeaContext buildContext(RawIdea idea, List<RefinementQA> answers)

#### DebateOrchestrator
List<DebateMessage> runDebate(IdeaContext context, int rounds) throws Exception

#### SynthesisOrchestrator
PipelineOutput synthesize(IdeaContext context, List<DebateMessage> debateHistory) throws Exception

#### CriticOrchestrator
CritiqueResult critique(PipelineOutput docs, List<DebateMessage> debateHistory)

#### ValidationOrchestrator
ValidationResult validate(PipelineOutput docs, CritiqueResult critique, int loopCount)

NOTA:
ValidationOrchestrator calcula convergenceScore SOLAMENTE.
NO aplica maxLoops — eso lo gestiona IdeaPipeline.

#### StackArchitectOrchestrator
ArchitectureDoc defineStack(IdeaContext context, PipelineOutput docs, List<DebateMessage> debateHistory)

#### PlanningPokerOrchestrator

TaskGraph runPlanningPoker(
  String tasksMarkdown,
  Team team,
  ArchitectureDoc architecture
) throws Exception

NOTA:
Este orchestrator es responsable de parsear tasksMarkdown a objetos Task internamente.

---

### 6. IdeaPipeline — esqueleto de orquestación

Inyecta todos los orchestrators.

Métodos:

List<String> startPipeline(RawIdea idea)

PipelineResult runFullPipeline(
  RawIdea idea,
  List<RefinementQA> answers,
  Team team,
  int debateRounds
) throws Exception

Privados:

IdeaContext enrichContextWithGaps(
  IdeaContext context,
  CritiqueResult critique
)

NOTA:
- IdeaPipeline es responsable del control del loop
- Evalúa:
  validation.shouldLoop AND loopCount < maxLoops

---

### 7. PipelineController

@RestController
@Validated
@Slf4j

Endpoints:

POST /pipeline/start
- body: RawIdea
- returns: List<String>

POST /pipeline/run
- body: RunRequest
- returns: PipelineResult

GET /pipeline/health
- returns:
  {
    "status": "ok",
    "version": "1.0"
  }

El controlador contiene ÚNICAMENTE lógica de delegación.

---

### 8. PipelineConfig

@Bean
ExecutorService virtualThreadExecutor()
→ Executors.newVirtualThreadPerTaskExecutor()

@ConfigurationProperties("pipeline")
public record PipelineProperties(
  int convergenceThreshold,
  int maxLoops,
  int defaultDebateRounds,
  int pokerConsensusMaxDistance
) {}

---

### 9. PipelineException

public class PipelineException extends RuntimeException {
  private final String phase;
  private final int loopCount;

  // constructor + getters
}

---

### 10. pom.xml

Usa:

- Java 21
- Spring Boot 4.1.0-SNAPSHOT
- spring-boot-starter-web
- spring-boot-starter-validation
- jackson-databind
- lombok
- spring-boot-starter-test (test scope)
- org.springframework.ai:spring-ai-bom:2.0.0-SNAPSHOT (scope: import, type: pom)

---

### 11. application.yml

spring:
  threads:
    virtual:
      enabled: true
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.7

pipeline:
  convergence-threshold: 75
  max-loops: 3
  default-debate-rounds: 2
  poker-consensus-max-distance: 1

---

## ESTÁNDARES DE CÓDIGO

- Inyección por constructor ÚNICAMENTE
- @Slf4j en todas partes
- Sin TODOs
- Records inmutables
- @NotNull donde sea necesario
- Sin lógica de negocio
- Sin lógica de parseo salvo en los casos explícitamente permitidos (PlanningPokerOrchestrator)
- Imports limpios

---

## CRITERIOS DE ACEPTACIÓN

1. mvn clean compile → SUCCESS
2. mvn spring-boot:run → STARTS
3. GET /pipeline/health → 200 OK
4. Sin clases faltantes
5. Sin TODOs
6. Sin desajustes de contrato
