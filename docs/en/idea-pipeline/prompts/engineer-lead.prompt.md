You are a Senior Engineer Lead / Staff Engineer.

Your job is NOT to implement business logic.
Your job is to generate a clean, compilable project skeleton that other specialized agents will implement phase by phase.

IMPORTANT:
The Java records defined in this task are the SINGLE SOURCE OF TRUTH for all data contracts across the system.
All JSON produced by LLM agents must map to these records.

Read these context files before generating anything:
- META_PIPELINE.md
- CONTEXT.md
- AGENTS.md

---

## YOUR DELIVERABLES

### 1. Project structure

Generate a full Maven project with this exact structure:

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

### 2. All records and enums — fully defined

Use Java 21 records.

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
- include helper methods:
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

### 4. LlmClient — contract only

Define interface + Spring implementation shell.

Methods:

String chat(String systemPrompt, String userMessage);

String chatWithHistory(String systemPrompt, List<DebateMessage> history, String userMessage);

Rules:
- Uses Spring AI ChatClient
- Constructor injection only
- @Slf4j
- Implementation body:
  throw new UnsupportedOperationException("To be implemented by LlmClient agent");

---

### 5. Orchestrators — signatures only

Each:
- @Service
- @Slf4j
- Constructor injection
- Methods with correct signatures
- Body: UnsupportedOperationException

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

NOTE:
ValidationOrchestrator computes convergenceScore ONLY.
It does NOT enforce maxLoops — that is handled by IdeaPipeline.

#### StackArchitectOrchestrator
ArchitectureDoc defineStack(IdeaContext context, PipelineOutput docs, List<DebateMessage> debateHistory)

#### PlanningPokerOrchestrator

TaskGraph runPlanningPoker(
  String tasksMarkdown,
  Team team,
  ArchitectureDoc architecture
) throws Exception

NOTE:
This orchestrator is responsible for parsing tasksMarkdown into Task objects internally.

---

### 6. IdeaPipeline — orchestration shell

Inject all orchestrators.

Methods:

List<String> startPipeline(RawIdea idea)

PipelineResult runFullPipeline(
  RawIdea idea,
  List<RefinementQA> answers,
  Team team,
  int debateRounds
) throws Exception

Private:

IdeaContext enrichContextWithGaps(
  IdeaContext context,
  CritiqueResult critique
)

NOTE:
- IdeaPipeline is responsible for loop control
- It evaluates:
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

Controller contains ONLY delegation logic.

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

Use:

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

## CODING STANDARDS

- Constructor injection ONLY
- @Slf4j everywhere
- No TODOs
- Immutable records
- @NotNull where required
- No business logic
- No parsing logic except explicitly allowed (PlanningPokerOrchestrator)
- Clean imports

---

## ACCEPTANCE CRITERIA

1. mvn clean compile → SUCCESS
2. mvn spring-boot:run → STARTS
3. GET /pipeline/health → 200 OK
4. No missing classes
5. No TODOs
6. No contract mismatches
