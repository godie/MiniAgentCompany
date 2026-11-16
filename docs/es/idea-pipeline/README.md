# Idea Pipeline

**Transforma ideas vagas en proyectos de software estructurados y estimables — impulsado por debate y convergencia multi‑agente con LLMs.**

[![Java 21](https://img.shields.io/badge/Java-21-blue)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0--SNAPSHOT-green)](https://spring.io/projects/spring-boot)
[![Tests](https://img.shields.io/badge/tests-87%2F87-brightgreen)](#)


---

## ¿Qué es esto?

Idea Pipeline es un **sistema de orquestación multi‑agente de LLMs** que toma una idea de software cruda y produce:

- ✅ Un contexto refinado y enriquecido (visión, usuarios, restricciones)
- ✅ Debate multi‑ronda entre agentes especializados (Product, Architect, Critic)
- ✅ Tres documentos estructurados: `context.md`, `flows.md`, `tasks.md`
- ✅ Un loop de convergencia con crítica y validación (score 0–100)
- ✅ Una definición completa del stack tecnológico
- ✅ Un grafo de tareas completamente estimado con story points (escala Fibonacci) y relaciones de dependencia

El pipeline fue **diseñado usándose a sí mismo** — la arquitectura original de 7 fases se convergió manualmente con Claude (Architect+Gatekeeper), DeepSeek (Critic) y Ollama/qwen3.5:4b (Validator).

---

## Las 7 fases

| Fase | Nombre | Naturaleza | Qué hace |
|-------|------|--------|-------------|
| **0** | Gatekeeper | Generativa | Genera hasta 6 preguntas de refinamiento a partir de una `RawIdea`; enriquece el contexto con las respuestas del usuario |
| **1** | Debate | Generativa | 3 agentes (Product, Architect, Critic) debaten durante varias rondas usando `StructuredTaskScope` |
| **2** | Synthesis | Generativa | 3 agentes en paralelo generan `context.md`, `flows.md` y `tasks.md` a partir del historial de debate |
| **3a** | Critique | Generativa | Audita los 3 documentos buscando inconsistencias, gaps y temas que necesitan volver a debatirse |
| **3b** | Validation | Generativa | Puntúa la convergencia de 0–100; decide si hacer loop (score < 75) o avanzar |
| **3.5** | Stack Architecture | Generativa | Define el stack tecnológico, los servicios, las restricciones, el modelo de despliegue y la justificación |
| **4** | Planning Poker | Ejecutiva | Parsea las tareas, simula la estimación del equipo con escala Fibonacci y construye el grafo de dependencias |

**Loop de convergencia:** las fases 2–3b se repiten hasta que `convergenceScore >= 75` (máx. 3 loops). La fase 4 se ejecuta una vez sobre documentos ya convergidos.

---

## Requisitos previos

| Dependencia | Versión | Notas |
|------------|---------|-------|
| **Java** | 21+ | Virtual threads + concurrencia estructurada |
| **Maven** | 3.8+ | Herramienta de build |
| **Ollama** | Última | Runtime local de LLM |
| **qwen3.5:4b** | — | Modelo por defecto (configurable en `application.yml`) |

### Instalar e iniciar Ollama

```bash
# Instalar (macOS)
brew install ollama

# Descargar el modelo por defecto
ollama pull qwen3.5:4b

# Iniciar el servidor (déjalo corriendo en otra terminal)
ollama serve
```

---

## Inicio rápido

### 1. Clonar y compilar

```bash
cd idea-pipeline
mvn clean compile
```

### 2. Ejecutar la aplicación

```bash
mvn spring-boot:run
```

El servidor arranca en `http://localhost:8080`.

### 3. Usar la API

**Paso 1: Obtener las preguntas de refinamiento**

```bash
curl -X POST http://localhost:8080/pipeline/start \
  -H "Content-Type: application/json" \
  -d '{"description": "A habit-tracking app with social accountability and AI coaching"}'
```

Respuesta:
```json
["Who is the primary user persona?", "What platform(s) should it support?", ...]
```

**Paso 2: Ejecutar el pipeline completo con tus respuestas**

```bash
curl -X POST http://localhost:8080/pipeline/run \
  -H "Content-Type: application/json" \
  -d '{
    "idea": {"description": "A habit-tracking app with social accountability and AI coaching"},
    "answers": [
      {"question": "Who is the primary user?", "answer": "Busy professionals 25-45"},
      {"question": "What platform?", "answer": "iOS first, Android later"}
    ],
    "team": {
      "members": [
        {
          "id": "dev-01",
          "name": "Alice",
          "baseRole": "BACKEND_DEV",
          "seniority": "SENIOR",
          "extraResponsibilities": []
        },
        {
          "id": "dev-02",
          "name": "Bob",
          "baseRole": "FRONTEND_DEV",
          "seniority": "MID",
          "extraResponsibilities": []
        }
      ]
    },
    "debateRounds": 2
  }'
```

La respuesta incluye un `PipelineResult` con el contexto enriquecido, los documentos completos, la arquitectura y el grafo de tareas estimado.

### Health check

```bash
curl http://localhost:8080/pipeline/health
# {"status": "ok", "version": "1.0"}
```

---

## Referencia de la API

| Endpoint | Método | Descripción |
|----------|--------|-------------|
| `/pipeline/start` | `POST` | Genera preguntas de refinamiento para una `RawIdea` |
| `/pipeline/run` | `POST` | Ejecuta el pipeline completo (las 7 fases) |
| `/pipeline/health` | `GET` | Health check |

### Valores por defecto

- **Sin equipo** → Usa un único `FULLSTACK_DEV` con seniority `MID`
- **Sin respuestas** → Usa una lista vacía (la fase 0 trabaja solo con la idea cruda)
- **`debateRounds` ≤ 0** → Usa `pipeline.default-debate-rounds` de la configuración (por defecto: 2)

---

## Configuración

Todos los parámetros del pipeline están en `src/main/resources/application.yml`:

```yaml
pipeline:
  convergence-threshold: 75    # Minimum score to advance past Phase 3b
  max-loops: 3                 # Max convergence loop iterations
  default-debate-rounds: 2     # Rounds per debate (Phase 1)
  poker-consensus-max-distance: 1  # Max Fibonacci gap for consensus
```

### Cambiar el modelo LLM

Edita `application.yml`:

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          model: llama3.2:3b   # or any other model you've pulled
          temperature: 0.7
```

---

## Estructura del proyecto

```
idea-pipeline/
├── src/main/java/com/ideapipeline/
│   ├── Application.java              # Spring Boot entry point
│   ├── config/
│   │   ├── PipelineConfig.java       # Bean definitions (StructuredTaskScope, ExecutorService)
│   │   └── PipelineProperties.java   # YAML binding (pipeline.*)
│   ├── controller/
│   │   └── PipelineController.java   # REST API (start, run, health)
│   ├── model/                        # Records & enums (RawIdea, Team, TaskGraph, etc.)
│   │   └── enums/                    # Seniority, BaseRole
│   ├── client/
│   │   ├── LlmClient.java            # Interface — chat() + chatWithHistory()
│   │   ├── LlmClientImpl.java        # Ollama implementation via Spring AI
│   │   └── JsonParser.java           # Markdown fence stripping + JSON extraction
│   ├── orchestrator/                 # One orchestrator per phase
│   │   ├── GatekeeperOrchestrator.java       # Phase 0
│   │   ├── DebateOrchestrator.java           # Phase 1
│   │   ├── SynthesisOrchestrator.java        # Phase 2
│   │   ├── CriticOrchestrator.java           # Phase 3a
│   │   ├── ValidationOrchestrator.java       # Phase 3b
│   │   ├── StackArchitectOrchestrator.java   # Phase 3.5
│   │   └── PlanningPokerOrchestrator.java    # Phase 4
│   ├── pipeline/
│   │   └── IdeaPipeline.java         # Central orchestrator — wires all phases
│   └── exception/
│       └── PipelineException.java    # Checked exception with phase metadata
├── src/test/java/com/ideapipeline/   # 87 tests across orchestrators, controller & integration
├── prompts/                          # System prompts for each phase
├── docs/
│   ├── phases/                       # Phase specs (PHASE-3A-SPEC.md, etc.)
│   ├── reports/                      # Implementation reports
│   ├── decisions/                    # ADRs (ADR-001, ADR-002)
│   ├── PLAN_SIGUIENTES_PASOS.md
│   ├── PLAN_FASES_3A_4.md
│   └── REVISION_PROYECTO.md
├── META_PIPELINE.md                  # Source of truth — pipeline design & canonical format
├── CONTEXT.md                        # Architecture decisions & project overview
├── AGENTS.md                         # Agent definitions & interaction diagrams
├── pom.xml
└── README.md                         # This file
```

---

## Ejecutar los tests

```bash
# Suite completa de tests (87 tests)
mvn test

# Clase de test específica
mvn test -Dtest=PlanningPokerOrchestratorTest

# Tests de controller + integración
mvn test -Dtest=PipelineControllerTest,IdeaPipelineIntegrationTest
```

---

## Decisiones de diseño clave

- **Virtual threads + `StructuredTaskScope`** en lugar de `CompletableFuture` — propagación de errores más limpia con `ShutdownOnFailure`
- **Stack definido antes del Planning Poker** (fase 3.5 → 4) — las estimaciones necesitan contexto técnico
- **Un único Scrum Master simula al equipo** (fase 4) — evita más de 100 llamadas al LLM por ejecución
- **Critique y Validation son fases separadas** (3a vs 3b) — responsabilidades distintas con prompts distintos
- **Todos los agentes usan Ollama/qwen3.5:4b** con system prompts distintos — sin costos de API externas
- **Formato canónico de intercambio** (`META_PIPELINE.md` sección 5) — ejecución manual reproducible

---

## Cómo contribuir

### Añadir una nueva fase

1. Crea un nuevo orquestador en `com.ideapipeline.orchestrator` siguiendo el patrón existente
2. Añade su system prompt en `prompts/`
3. Conéctalo en `IdeaPipeline.java` en la posición correcta de la secuencia
4. Escribe tests unitarios siguiendo las convenciones del proyecto (`@ExtendWith(MockitoExtension.class)`, mock de `LlmClient`)
5. Actualiza `META_PIPELINE.md` si el flujo cambia
6. Actualiza `AGENTS.md` con la definición del nuevo agente y el diagrama de interacción
7. Ejecuta `mvn test` para verificar que no hay regresiones

### Convenciones de código

- **Records de Java 21** para todos los modelos de datos (inmutables, accessors autogenerados)
- **Lombok** para reducir boilerplate (`@Slf4j`, `@RequiredArgsConstructor`)
- **`@ExtendWith(MockitoExtension.class)`** para tests unitarios (sin contexto de Spring)
- **`@SpringBootTest` + `@MockitoBean`** para tests de integración
- Javadoc en todas las clases y métodos públicos
- Tests nombrados `methodName_shouldExpectedBehavior_whenCondition`

### Documentación

- `META_PIPELINE.md` — diseño canónico del pipeline (fuente de verdad)
- `CONTEXT.md` — decisiones de arquitectura y descripción general del proyecto
- `AGENTS.md` — definiciones de agentes por fase
- `docs/phases/` — specs detalladas por fase
- `docs/reports/` — reportes de implementación por fase
- `docs/decisions/` — Architecture Decision Records

---

---

*Construido con Java 21, Spring Boot, Spring AI y Ollama.*
