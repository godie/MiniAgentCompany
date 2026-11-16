# phase-3b-validation.prompt.md v1.0

## Rol
Eres un Desarrollador Java Senior que implementa la Fase 3b del sistema idea-pipeline.
La Fase 3b evalúa si los tres documentos sintetizados han convergido
lo suficiente como para avanzar a la definición de la arquitectura, o si se
necesita otro ciclo debate→síntesis.

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

- `PipelineOutput.java` — record, no modificar.
- `CritiqueResult.java` — record, no modificar.
- `ValidationResult.java` — record, no modificar:
  `ValidationResult(int convergenceScore, boolean shouldLoop, String reasoning)`

- `PipelineException.java` — constructores disponibles.

- `IdeaPipeline.java` — gestiona la política de `maxLoops` externamente.
  Tu orquestador NO aplica maxLoops; solo calcula la puntuación
  y la recomendación `shouldLoop`.

- `ValidationOrchestrator.java` — TU ARCHIVO OBJETIVO
  Actualmente usa una fórmula ficticia: `score = max(10, 100 - loopCount * 5)`.
  Inyecta `LlmClient` mediante constructor:
  `public ValidationOrchestrator(LlmClient llmClient)`

---

## Estado del proyecto
- `mvn clean compile` → SUCCESS
- `mvn test` → 38/38 pasando

---

## Tu tarea

Implementa `ValidationOrchestrator.java` — un método: `validate()`

---

### Constante del system prompt

```java
private static final String VALIDATION_SYSTEM_PROMPT = """
You are a convergence evaluator for a software design pipeline.
You receive three documents (context, flows, tasks) and the results
of a prior audit (critiques, gaps, refinement needs).

Your job is to decide whether the documents are sufficiently coherent
and complete to advance to architecture definition.

Score rubric (0-100):

0-40 — CRITICAL: Major inconsistencies, contradictions, or missing sections.
        Must loop. Documents are not usable.

41-74 — NEEDS WORK: Significant gaps or vague sections exist, but the core
         is directionally correct. Looping is recommended but not forced.

75-89 — GOOD: Documents are coherent and mostly complete. Minor gaps
         are acceptable. Should advance.

90-100 — EXCELLENT: Documents are thorough, consistent, and ready for
          architecture definition. Definitely advance.

Your shouldLoop recommendation:
- true  → send back to debate+synthesis
- false → advance to architecture

IMPORTANT: Consider the loopCount in your reasoning.
- If loopCount >= 2, be more permissive (the system is converging slowly).
- If this is the first pass (loopCount = 0), be rigorous.

Respond ONLY in JSON with this exact format, no preamble, no markdown:
{
  "convergenceScore": 82,
  "shouldLoop": false,
  "reasoning": "Brief explanation of the score and decision (max 150 words)"
}
""";
```

---

## Implementación: `validate()`

**Firma:**
```java
public ValidationResult validate(PipelineOutput docs, CritiqueResult critique, int loopCount)
```

**Propósito:** Evaluar la convergencia de los documentos usando un LLM para asignar una
puntuación y decidir si se necesita otro ciclo debate→síntesis.

---

### Pasos de implementación

1. **Validar las entradas**
   - Si `docs == null`: lanzar `PipelineException("docs is null", "Phase3b", loopCount)`
   - Si `critique == null`: lanzar `PipelineException("critique is null", "Phase3b", loopCount)`

2. **Construir el mensaje de usuario**

   ```
   === LOOP COUNT: [loopCount] ===

   --- CONTEXT DOCUMENT ---
   [docs.contextDocument()]

   --- FLOW DOCUMENT ---
   [docs.flowDocument()]

   --- TASK DOCUMENT ---
   [docs.taskDocument()]

   --- AUDIT RESULTS ---
   Critiques found:
   - [critique.critiques() joined by "\n- "]

   Gaps identified:
   - [critique.gaps() joined by "\n- "]

   Refinement needs:
   - [critique.refinementNeeds() joined by "\n- "]
   ```

   - Si alguna lista de críticas está vacía, escribe: `(none)`
   - Incluye el loopCount de forma destacada para que el LLM pueda tenerlo en cuenta
     en la tolerancia de convergencia

3. **Llamar al LLM**
   ```java
   String response = llmClient.chat(VALIDATION_SYSTEM_PROMPT, userMessage);
   ```

4. **Validar la respuesta**
   - Si `response == null || response.isBlank()`:
     lanzar `PipelineException("Empty response from LLM", "Phase3b", loopCount)`

5. **Parsear el JSON**
   ```java
   try {
       JsonNode node = JsonParser.parse(response);
       
       int convergenceScore = node.get("convergenceScore").asInt();
       boolean shouldLoop = node.get("shouldLoop").asBoolean();
       String reasoning = node.get("reasoning").asText();
       
       // Validar el rango de la puntuación
       if (convergenceScore < 0 || convergenceScore > 100) {
           throw new PipelineException(
               "Invalid convergenceScore: " + convergenceScore + " (must be 0-100)",
               "Phase3b", loopCount);
       }
       
       return new ValidationResult(convergenceScore, shouldLoop, reasoning);
   } catch (PipelineException e) {
       throw e;
   } catch (RuntimeException e) {
       throw new PipelineException("Failed to parse validation JSON", "Phase3b", loopCount, e);
   }
   ```

---

## Logging

- `log.info()` al entrar al método: loopCount y longitudes de los documentos
- `log.info()` tras el éxito: convergenceScore, shouldLoop, resumen del reasoning
- `log.warn()` si convergenceScore < 75 y shouldLoop == true (a punto de hacer loop)
- `log.info()` si convergenceScore >= 75 y shouldLoop == false (avanzando)
- `log.error()` ante cualquier excepción capturada antes de relanzarla

---

## Manejo de excepciones

```java
// Patrón:
try {
    // ... llamada al LLM + parseo del JSON ...
} catch (PipelineException e) {
    throw e;
} catch (RuntimeException e) {
    throw new PipelineException("Validation failed", "Phase3b", loopCount, e);
}
```

---

## Estándares de código

- `@Service` + `@Slf4j` en la clase
- Solo inyección por constructor — `public ValidationOrchestrator(LlmClient llmClient)`
- Sin `@Autowired` en campos
- System prompt como constante `private static final String`
- Todo el parseo de JSON mediante `JsonParser.parse()`
- Todas las excepciones del LLM envueltas en `PipelineException`
- Sin fórmula ficticia — la puntuación DEBE venir de la evaluación del LLM
- Eliminar por completo la lógica de placeholder `Math.max(10, 100 - loopCount * 5)`
- Validación del rango de la puntuación (0-100) después del parseo

---

## Requisitos de testing (TDD)

Archivo de test: `src/test/java/com/ideapipeline/orchestrator/ValidationOrchestratorTest.java`
Usa `@ExtendWith(MockitoExtension.class)` y mockea `LlmClient`.

### Tests requeridos

1. **`validate_shouldReturnParsedValidationResult`**
   - Mockea `llmClient.chat()` para devolver:
     ```json
     {"convergenceScore":82,"shouldLoop":false,"reasoning":"Documents are coherent"}
     ```
   - Verifica `result.convergenceScore() == 82`
   - Verifica `result.shouldLoop() == false`
   - Verifica que `result.reasoning()` sea igual a `"Documents are coherent"`

2. **`validate_shouldReturnShouldLoopTrue_whenScoreIsLow`**
   - Mockea la respuesta con `convergenceScore: 35, shouldLoop: true`
   - Verifica `result.shouldLoop() == true`

3. **`validate_shouldThrowPipelineException_onInvalidJson`**
   - Mockea `llmClient.chat()` para devolver `"not json"`
   - Verifica que se lanza `PipelineException` con fase `"Phase3b"`

4. **`validate_shouldThrowPipelineException_onEmptyResponse`**
   - Mockea `llmClient.chat()` para devolver `""`
   - Verifica que se lanza `PipelineException` con fase `"Phase3b"`

5. **`validate_shouldThrowPipelineException_onScoreOutOfRange`**
   - Mockea la respuesta con `convergenceScore: 150`
   - Verifica que se lanza `PipelineException` con un mensaje que contiene "must be 0-100"

6. **`validate_shouldIncludeLoopCountInPrompt`**
   - Usa `ArgumentCaptor<String>` para capturar el `userMessage`
   - Verifica que contiene `"=== LOOP COUNT: 2 ==="` (o el loopCount que sea)

7. **`validate_shouldIncludeAllDocumentsInPrompt`**
   - Verifica que `userMessage` contiene contextDocument, flowDocument y taskDocument

8. **`validate_shouldIncludeCritiqueResultsInPrompt`**
   - Verifica que `userMessage` contiene el texto de las críticas y el texto de los gaps

9. **`validate_shouldHandleMarkdownFencedResponse`**
   - Mockea `llmClient.chat()` para devolver JSON envuelto en ```json fences
   - Verifica que `JsonParser` elimina los fences y el resultado se parsea correctamente

10. **`validate_shouldHandleEmptyCritiqueLists`**
    - Pasa un `CritiqueResult` con listas vacías
    - Verifica que el userMessage contiene `"(none)"` en las secciones vacías

11. **`validate_shouldThrowPipelineException_onLlmFailure`**
    - Mockea `llmClient.chat()` para lanzar `RuntimeException("LLM error")`
    - Verifica que se lanza `PipelineException` con fase `"Phase3b"`

12. **`validate_shouldNotUseDummyFormula`**
    - Este test verifica que el orquestador realmente llama a `llmClient.chat()`
    - Usa `verify(llmClient, times(1)).chat(anyString(), anyString())`
    - Si la verificación falla, la fórmula ficticia sigue en uso

---

## Criterios de aceptación

1. `mvn clean compile` → SUCCESS
2. `mvn test` → 12 tests nuevos pasan (ValidationOrchestratorTest)
3. Total de tests ≥ 50 pasando
4. `validate()` NO tiene fórmula ficticia — la puntuación viene solo del LLM
5. El rango de la puntuación (0-100) se valida después del parseo
6. La decisión `shouldLoop` viene del LLM, no de lógica hardcodeada
7. `loopCount` se incluye en el prompt del LLM
8. Las respuestas nulas o vacías del LLM lanzan `PipelineException`
9. La RuntimeException de `JsonParser` se envuelve en `PipelineException`
10. Los tres documentos y los resultados de la crítica aparecen en el prompt
11. El system prompt definido como constante `private static final String`
12. El orquestador NO aplica `maxLoops` (eso es tarea de `IdeaPipeline`)

---

## Archivos a modificar

1. **`src/main/java/com/ideapipeline/orchestrator/ValidationOrchestrator.java`**
   - Inyectar `LlmClient` mediante constructor
   - Reemplazar la fórmula ficticia por evaluación basada en LLM
   - Añadir la validación del rango de la puntuación

2. **`src/test/java/com/ideapipeline/orchestrator/ValidationOrchestratorTest.java`**
   - Crear un archivo de test nuevo (o reemplazar el existente si hay uno)
   - 12 métodos de test según lo especificado arriba

---

## Criterios de éxito

Después de la implementación:
- ✅ El código compila sin warnings
- ✅ Los 12 tests pasan
- ✅ No queda ninguna fórmula ficticia
- ✅ El LLM se llama realmente (verificado por la verificación del mock)
- ✅ Manejo de excepciones adecuado en todo el flujo
- ✅ Logging en los niveles apropiados

---

Fin de phase-3b-validation.prompt.md v1.0
