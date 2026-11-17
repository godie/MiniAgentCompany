# phase-3b-validation.prompt.md v1.1

## Rol
Eres un desarrollador Java Senior que implementa la Fase 3b del sistema idea-pipeline.
La Fase 3b recibe los 3 documentos + la crítica de la Fase 3a y decide
si el pipeline ha convergido o necesita otro bucle.

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
- `GatekeeperOrchestrator.java` — completamente implementado. 8 tests pasando.
- `DebateOrchestrator.java` — completamente implementado. 7 tests pasando.
- `SynthesisOrchestrator.java` — completamente implementado. 8 tests pasando.
- `CriticOrchestrator.java` — completamente implementado. 11 tests pasando.
- `PipelineOutput.java`, `CritiqueResult.java`, `ValidationResult.java` — records, no modificar:
  - `CritiqueResult(List<String> critiques, List<String> gaps, List<String> refinementNeeds)`
  - `ValidationResult(int convergenceScore, boolean shouldLoop, String reasoning)`
- `PipelineConfig.java` — tiene PipelineProperties con convergenceThreshold y maxLoops
- `PipelineProperties.java` — inyectar para leer convergenceThreshold
- `PipelineException.java` — constructores:
  - `PipelineException(String message, String phase, int loopCount)`
  - `PipelineException(String message, String phase, int loopCount, Throwable cause)`
- `ValidationOrchestrator.java` — TU ARCHIVO OBJETIVO.
  Inyecta `LlmClient` y `PipelineProperties` mediante constructor.

## Estado del proyecto
- mvn clean compile → SUCCESS
- mvn test → 40/40 pasando

---

## Tu tarea

Implementa `ValidationOrchestrator.java` — un método: `validate()`

---

## Prompt del sistema — definir como constante private static final String

```java
private static final String VALIDATION_SYSTEM_PROMPT = """
    You are a convergence evaluator for a multi-agent pipeline.
    You receive 3 documents and a critique report and must decide
    if the pipeline has converged or needs another iteration.

    convergenceScore rules:
    - 0-40: major inconsistencies, loop mandatory
    - 41-74: significant gaps, loop recommended
    - 75-100: documents are coherent, advance

    Respond ONLY in JSON with this exact format, no preamble, no markdown:
    {
      "convergenceScore": 85,
      "shouldLoop": false,
      "reasoning": "explanation"
    }
    """;
```

---

## Método: validate()

**Firma:**
```java
public ValidationResult validate(
    PipelineOutput docs,
    CritiqueResult critique,
    int loopCount
)
```

**Propósito:** Enviar los 3 documentos + la crítica al LLM validador y parsear la respuesta JSON. El campo `shouldLoop` de la respuesta es orientativo — `IdeaPipeline` aplica el límite maxLoops, NO este método.

**Formato exacto de userMessage:**

Cada lista de `CritiqueResult` se serializa línea por línea, un elemento por línea, con el prefijo `- `. Si una lista es null o está vacía, escribe `- (none)` en su lugar.

```
=== DOCUMENTS ===
--- CONTEXT ---
{docs.contextDocument()}
--- FLOWS ---
{docs.flowDocument()}
--- TASKS ---
{docs.taskDocument()}
=== CRITIQUE (iteration {loopCount}) ===
Critiques:
- critique item 1
- critique item 2

Gaps:
- gap item 1

Refinement needs:
- refinement item 1
- refinement item 2
```

**Ejemplo concreto** — dado `loopCount = 2` y un `CritiqueResult` con:
- `critiques = ["Missing error handling", "Inconsistent naming"]`
- `gaps = ["No deployment section"]`
- `refinementNeeds = []`

La sección de crítica debe renderizarse como:
```
=== CRITIQUE (iteration 2) ===
Critiques:
- Missing error handling
- Inconsistent naming

Gaps:
- No deployment section

Refinement needs:
- (none)
```

**Pasos de implementación:**

1. Validar las entradas:
   - Si `docs` es null: lanzar `PipelineException("docs is null", "Phase3b", 0)`
   - Si `critique` es null: lanzar `PipelineException("critique is null", "Phase3b", loopCount)`
   - Si `loopCount < 0`: lanzar `PipelineException("loopCount must be >= 0", "Phase3b", loopCount)`

2. Crear un helper privado `formatList(List<String> items)` que devuelva un String:
   - Si `items` es null o está vacía: devolver `"- (none)"`
   - En caso contrario: unir cada elemento como `"- " + item`, uno por línea (`\n`)

3. Construir `userMessage` usando el formato exacto de arriba, llamando a `formatList()` para cada una de las tres listas de la crítica

4. Llamar a `llmClient.chat(VALIDATION_SYSTEM_PROMPT, userMessage)`

5. Si la respuesta es null o está en blanco:
   lanzar `PipelineException("Empty response from validation LLM", "Phase3b", loopCount)`

6. Parsear con `JsonParser.parse(response)` — envolver RuntimeException:
   lanzar `PipelineException("Failed to parse validation JSON", "Phase3b", loopCount, e)`

7. Extraer del JsonNode:
   - `convergenceScore` → int (por defecto `0` si falta)
   - `shouldLoop` → boolean (por defecto `true` si falta)
   - `reasoning` → String (por defecto `""` si falta)

8. Devolver `new ValidationResult(convergenceScore, shouldLoop, reasoning)`

**Logging:**
- `log.info()` al entrar: loopCount + tamaño de cada lista de la crítica
- `log.info()` después del parseo: convergenceScore y shouldLoop
- `log.warn()` si `convergenceScore < convergenceThreshold`
- `log.error()` en cualquier excepción capturada antes de relanzarla

---

## Estándares de codificación
- Inyección solo por constructor
- @Service + @Slf4j
- Sin @Autowired en los campos
- Todo el parseo JSON mediante `JsonParser.parse()`
- Prompt del sistema como constante private static final String
- Los campos JSON faltantes usan valores por defecto seguros (nunca null, nunca excepción)
- `formatList()` es un método helper privado — no expuesto públicamente
- Este método NO aplica maxLoops — esa es responsabilidad de IdeaPipeline

---

## Requisitos de pruebas (TDD)

Archivo de test: `src/test/java/com/ideapipeline/orchestrator/ValidationOrchestratorTest.java`

### Tests requeridos

- `validate_shouldReturnParsedValidationResult`
  Respuesta mock: `{"convergenceScore": 85, "shouldLoop": false, "reasoning": "good"}`
  Verificar convergenceScore == 85, shouldLoop == false, reasoning == "good"

- `validate_shouldHandleMarkdownFencedResponse`
  Respuesta mock con vallas ` ```json `.
  Verificar que ValidationResult se parsea correctamente.

- `validate_shouldUseDefaultsForMissingFields`
  Respuesta mock: `{"convergenceScore": 50}` (sin shouldLoop, sin reasoning)
  Verificar shouldLoop == true, reasoning == ""

- `validate_shouldThrowPipelineException_onNullDocs`
  Verificar PipelineException con fase "Phase3b"

- `validate_shouldThrowPipelineException_onNullCritique`
  Verificar PipelineException con fase "Phase3b"

- `validate_shouldThrowPipelineException_onNegativeLoopCount`
  Pasar loopCount = -1.
  Verificar PipelineException con fase "Phase3b"

- `validate_shouldThrowPipelineException_onEmptyLlmResponse`
  Mockear `llmClient.chat()` para que devuelva `""`
  Verificar PipelineException con fase "Phase3b"

- `validate_shouldThrowPipelineException_onInvalidJson`
  Mockear `llmClient.chat()` para que devuelva `"not json"`
  Verificar PipelineException con fase "Phase3b"

- `validate_shouldIncludeLoopCountInUserMessage`
  Usar ArgumentCaptor para capturar userMessage.
  Verificar que contiene `"iteration 2"` cuando loopCount == 2.

- `validate_shouldFormatCritiqueItemsAsBulletLines`
  Pasar CritiqueResult con las críticas conocidas `["C1", "C2"]`, gaps `["G1"]`, refinementNeeds `[]`.
  Usar ArgumentCaptor para capturar userMessage.
  Verificar que userMessage contiene:
  - `"- C1\n- C2"` bajo `Critiques:`
  - `"- G1"` bajo `Gaps:`
  - `"- (none)"` bajo `Refinement needs:`

- `validate_shouldRenderNoneForNullOrEmptyLists`
  Pasar CritiqueResult con las tres listas vacías.
  Usar ArgumentCaptor.
  Verificar que userMessage contiene `"- (none)"` exactamente 3 veces.

---

## Criterios de aceptación
1. `mvn clean compile` → SUCCESS
2. `mvn test` → 11 tests nuevos pasan (ValidationOrchestratorTest)
3. `mvn test` → 51 tests en total pasando
4. `validate()` no tiene `UnsupportedOperationException`
5. Los campos JSON faltantes usan valores por defecto seguros (no null, no excepción)
6. Este método NO aplica maxLoops
7. `convergenceScore < threshold` dispara `log.warn()`
8. Cada elemento de las listas de la crítica aparece como `"- item"` en el userMessage
9. Una lista null o vacía se renderiza como `"- (none)"`
10. `loopCount < 0` lanza PipelineException
11. Prompt del sistema definido como constante private static final

---
