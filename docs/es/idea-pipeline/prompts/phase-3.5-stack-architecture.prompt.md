# phase-3.5-stack-architecture.prompt.md v1.0

## Rol
Eres un Desarrollador Java Senior que implementa la Fase 3.5 del sistema idea-pipeline.
La Fase 3.5 se ejecuta DESPUÉS de alcanzar la convergencia. Define el stack
tecnológico completo, la topología de servicios, las restricciones, el modelo de despliegue
y la justificación arquitectónica, a partir de los documentos convergidos y del historial de debate.

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

- `IdeaContext.java` — record, no modificar.
- `DebateMessage.java` — record, no modificar.
- `PipelineOutput.java` — record, no modificar.
- `ArchitectureDoc.java` — record, no modificar:
  `ArchitectureDoc(String stack, List<String> services, List<String> constraints, String deploymentModel, String rationale)`

- `PipelineException.java` — constructores disponibles.

- `StackArchitectOrchestrator.java` — TU ARCHIVO OBJETIVO
  Actualmente devuelve un `ArchitectureDoc` hardcodeado.
  Inyecta `LlmClient` mediante constructor:
  `public StackArchitectOrchestrator(LlmClient llmClient)`

---

## Estado del proyecto
- `mvn clean compile` → SUCCESS
- `mvn test` → 50/50 pasando

---

## Tu tarea

Implementa `StackArchitectOrchestrator.java` — un método: `defineStack()`

---

### Constante del system prompt

```java
private static final String ARCHITECT_SYSTEM_PROMPT = """
You are a Senior Software Architect with 15+ years of experience across
cloud-native, on-premise, and hybrid architectures.

Given converged documents from a software design pipeline (context, flows, tasks),
plus debate history, define the COMPLETE technology stack and architecture.

Make decisions considering:
- Scalability requirements implied by the flows
- Team complexity (prefer boring technology unless requirements demand otherwise)
- Time-to-market vs. long-term maintainability trade-offs
- Cost (infrastructure, licensing, operational overhead)
- Existing ecosystem compatibility

Your output:

1. **stack**: Primary technologies — language, framework, database, message broker,
   caching, monitoring, CI/CD. Format as comma-separated string.
   Example: "Java 21 + Spring Boot 4.x + PostgreSQL 16 + Redis + Kafka + Grafana + GitHub Actions"

2. **services**: List of deployable services/modules. Each with a clear single responsibility.
   Example: ["API Gateway (rate limiting, auth)", "Auth Service (OAuth2, session mgmt)",
   "Core Business Service", "Notification Worker"]

3. **constraints**: Non-functional requirements and technical constraints.
   Example: ["Must run on Kubernetes 1.30+", "p95 latency < 200ms",
   "GDPR-compliant data storage", "Zero-downtime deployments"]

4. **deploymentModel**: How the system is deployed and orchestrated.
   Example: "Microservices on Kubernetes (EKS) with Istio service mesh,
   ArgoCD for GitOps, RDS for PostgreSQL"

5. **rationale**: Why these choices. Justify each major decision.
   Max 250 words. Be specific — reference the documents.
   Example: "Spring Boot chosen for team familiarity and virtual thread support.
   PostgreSQL for ACID guarantees on payment data. Redis for session cache
   to meet the <50ms auth flow requirement from flows.md."

Respond ONLY in JSON with this exact format, no preamble, no markdown:
{
  "stack": "...",
  "services": ["svc1", "svc2"],
  "constraints": ["c1", "c2"],
  "deploymentModel": "...",
  "rationale": "..."
}

Services and constraints: provide 3-8 items each.
Do NOT return empty arrays.
""";
```

---

## Implementación: `defineStack()`

**Firma:**
```java
public ArchitectureDoc defineStack(IdeaContext context, PipelineOutput documents, List<DebateMessage> debateHistory)
```

**Propósito:** Definir el stack tecnológico completo y la arquitectura a partir de
los documentos convergidos, el contexto enriquecido y las ideas del debate.

---

### Pasos de implementación

1. **Validar las entradas**
   - Si `context == null`: lanzar `PipelineException("context is null", "Phase3.5", 0)`
   - Si `documents == null`: lanzar `PipelineException("documents is null", "Phase3.5", 0)`
   - Si `debateHistory == null`: lanzar `PipelineException("debateHistory is null", "Phase3.5", 0)`

2. **Construir el mensaje de usuario**

   ```
   === IDEA CONTEXT ===
   [context.enrichedSummary()]

   --- CONTEXT DOCUMENT (context.md) ---
   [documents.contextDocument()]

   --- FLOW DOCUMENT (flows.md) ---
   [documents.flowDocument()]

   --- TASK DOCUMENT (tasks.md) ---
   [documents.taskDocument()]

   --- DEBATE INSIGHTS ---
   [agentName] (round N): [content]
   ...
   ```

   - Si `debateHistory` está vacío: `"(No debate history available)"`
   - Formatea cada DebateMessage como: `[agentName] (round N): [content]`
   - ⚠️ **Presupuesto de tokens**: si `debateHistory.size() > 30`, incluye solo
     los primeros 5 y los últimos 10 mensajes, con un aviso de truncamiento

3. **Llamar al LLM**
   ```java
   String response = llmClient.chat(ARCHITECT_SYSTEM_PROMPT, userMessage);
   ```

4. **Validar la respuesta**
   - Si `response == null || response.isBlank()`:
     lanzar `PipelineException("Empty response from LLM", "Phase3.5", 0)`

5. **Parsear el JSON**
   ```java
   try {
       JsonNode node = JsonParser.parse(response);
       
       String stack = node.get("stack").asText();
       List<String> services = extractStringList(node, "services");
       List<String> constraints = extractStringList(node, "constraints");
       String deploymentModel = node.get("deploymentModel").asText();
       String rationale = node.get("rationale").asText();
       
       // Validar los campos obligatorios
       if (stack.isBlank()) {
           throw new PipelineException("Empty stack in architecture response", "Phase3.5", 0);
       }
       if (services.isEmpty()) {
           throw new PipelineException("Empty services list in architecture response", "Phase3.5", 0);
       }
       
       return new ArchitectureDoc(stack, services, constraints, deploymentModel, rationale);
   } catch (PipelineException e) {
       throw e;
   } catch (RuntimeException e) {
       throw new PipelineException("Failed to parse architecture JSON", "Phase3.5", 0, e);
   }
   ```

6. **Método helper de extracción**
   ```java
   private List<String> extractStringList(JsonNode node, String fieldName) {
       JsonNode array = node.get(fieldName);
       if (array == null || !array.isArray()) {
           log.warn("Field '{}' missing or not an array in architecture response", fieldName);
           return List.of();
       }
       List<String> items = new ArrayList<>();
       for (JsonNode item : array) {
           items.add(item.asText());
       }
       return items;
   }
   ```

---

## Logging

- `log.info()` al entrar al método: longitud del resumen del contexto, longitudes de los documentos, tamaño de debateHistory
- `log.info()` tras el éxito: stack, número de servicios, número de restricciones
- `log.warn()` si debateHistory está vacío
- `log.warn()` si debateHistory se trunca por el presupuesto de tokens
- `log.warn()` si falta algún campo del JSON (fallback a vacío/por defecto)
- `log.error()` ante cualquier excepción capturada antes de relanzarla

---

## Manejo de excepciones

```java
try {
    // ... llamada al LLM + parseo del JSON + validación ...
} catch (PipelineException e) {
    throw e;
} catch (RuntimeException e) {
    throw new PipelineException("Architecture definition failed", "Phase3.5", 0, e);
}
```

---

## Estándares de código

- `@Service` + `@Slf4j` en la clase
- Solo inyección por constructor — `public StackArchitectOrchestrator(LlmClient llmClient)`
- Sin `@Autowired` en campos
- System prompt como constante `private static final String`
- Todo el parseo de JSON mediante `JsonParser.parse()`
- Todas las excepciones del LLM envueltas en `PipelineException`
- Sin datos de arquitectura hardcodeados — toda la salida viene del LLM
- Eliminar por completo todas las cadenas de placeholder hardcodeadas
- Validar los campos obligatorios después del parseo (stack no vacío, services no vacío)

---

## Requisitos de testing (TDD)

Archivo de test: `src/test/java/com/ideapipeline/orchestrator/StackArchitectOrchestratorTest.java`
Usa `@ExtendWith(MockitoExtension.class)` y mockea `LlmClient`.

### Tests requeridos

1. **`defineStack_shouldReturnParsedArchitectureDoc`**
   - Mockea `llmClient.chat()` para devolver:
     ```json
     {
       "stack": "Java 21 + Spring Boot + PostgreSQL + Redis",
       "services": ["API Gateway", "Auth Service", "Core Service"],
       "constraints": ["Kubernetes", "p95 < 200ms"],
       "deploymentModel": "Microservices on K8s",
       "rationale": "Team familiar with Spring Boot. PostgreSQL for ACID."
     }
     ```
   - Verifica que los 5 campos coinciden

2. **`defineStack_shouldThrowPipelineException_onInvalidJson`**
   - Mockea `llmClient.chat()` para devolver `"not json"`
   - Verifica que se lanza `PipelineException` con fase `"Phase3.5"`

3. **`defineStack_shouldThrowPipelineException_onEmptyResponse`**
   - Mockea `llmClient.chat()` para devolver `""`
   - Verifica que se lanza `PipelineException` con fase `"Phase3.5"`

4. **`defineStack_shouldThrowPipelineException_onEmptyStack`**
   - Mockea la respuesta con `"stack": ""`
   - Verifica que se lanza `PipelineException` con un mensaje que contiene "Empty stack"

5. **`defineStack_shouldThrowPipelineException_onEmptyServices`**
   - Mockea la respuesta con `"services": []`
   - Verifica que se lanza `PipelineException` con un mensaje que contiene "Empty services"

6. **`defineStack_shouldIncludeAllDocumentsInPrompt`**
   - Usa `ArgumentCaptor<String>` para capturar el `userMessage`
   - Verifica que contiene el resumen del contexto, contextDocument, flowDocument y taskDocument

7. **`defineStack_shouldIncludeDebateHistoryInPrompt`**
   - Proporciona un debateHistory con 3 mensajes
   - Verifica que `userMessage` contiene el formato `[agentName] (round N): [content]`

8. **`defineStack_shouldHandleMarkdownFencedResponse`**
   - Mockea la respuesta envuelta en ```json fences
   - Verifica que `JsonParser` elimina los fences y el resultado se parsea correctamente

9. **`defineStack_shouldHandleMissingConstraints`**
   - Mockea la respuesta sin el campo `"constraints"`
   - Verifica que `result.constraints()` es una lista vacía (no null)

10. **`defineStack_shouldThrowPipelineException_onLlmFailure`**
    - Mockea `llmClient.chat()` para lanzar `RuntimeException("LLM error")`
    - Verifica que se lanza `PipelineException` con fase `"Phase3.5"`

11. **`defineStack_shouldHandleEmptyDebateHistory`**
    - Pasa `List.of()` como debateHistory
    - Verifica que no hay excepción y que userMessage contiene `"(No debate history available)"`

---

## Criterios de aceptación

1. `mvn clean compile` → SUCCESS
2. `mvn test` → 11 tests nuevos pasan (StackArchitectOrchestratorTest)
3. Total de tests ≥ 61 pasando
4. `defineStack()` NO tiene datos hardcodeados — toda la salida viene del LLM
5. Los 5 campos de `ArchitectureDoc` poblados desde el JSON parseado
6. Stack vacío o services vacío lanzan `PipelineException`
7. Los campos opcionales ausentes (constraints) usan por defecto una lista vacía
8. Las respuestas nulas o vacías del LLM lanzan `PipelineException`
9. La RuntimeException de `JsonParser` se envuelve en `PipelineException`
10. Los tres documentos + el contexto de la idea + el historial de debate aparecen en el prompt
11. El system prompt definido como constante `private static final String`

---

## Archivos a modificar

1. **`src/main/java/com/ideapipeline/orchestrator/StackArchitectOrchestrator.java`**
   - Inyectar `LlmClient` mediante constructor
   - Reemplazar el placeholder hardcodeado por la definición de arquitectura basada en LLM
   - Añadir el método helper `extractStringList()`

2. **`src/test/java/com/ideapipeline/orchestrator/StackArchitectOrchestratorTest.java`**
   - Crear un archivo de test nuevo (o reemplazar el existente si hay uno)
   - 11 métodos de test según lo especificado arriba

---

## Criterios de éxito

Después de la implementación:
- ✅ El código compila sin warnings
- ✅ Los 11 tests pasan
- ✅ No quedan datos de arquitectura hardcodeados
- ✅ Manejo de excepciones adecuado en todo el flujo
- ✅ Logging en los niveles apropiados
- ✅ Validación de los campos obligatorios después del parseo del JSON

---

Fin de phase-3.5-stack-architecture.prompt.md v1.0
