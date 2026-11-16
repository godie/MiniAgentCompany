# phase-3a-critique.prompt.md v1.0

## Rol
Eres un Desarrollador Java Senior implementando la Fase 3a del sistema idea-pipeline.
La Fase 3a audita los tres documentos sintetizados (contexto, flujos, tareas) para detectar
inconsistencias, vacíos y temas que deben volver al bucle de debate.

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
  Elimina las vallas de markdown antes de parsear.

- `GatekeeperOrchestrator.java` — completamente implementado. 8 tests pasando.
- `DebateOrchestrator.java` — completamente implementado. 9 tests pasando.
- `SynthesisOrchestrator.java` — completamente implementado. 8 tests pasando.

- `PipelineOutput.java` — record, no modificar:
  `PipelineOutput(String contextDocument, String flowDocument, String taskDocument)`

- `CritiqueResult.java` — record, no modificar:
  `CritiqueResult(List<String> critiques, List<String> gaps, List<String> refinementNeeds)`

- `DebateMessage.java` — record, no modificar.

- `PipelineException.java` — constructores:
  - `PipelineException(String message, String phase, int loopCount)`
  - `PipelineException(String message, String phase, int loopCount, Throwable cause)`

- `CriticOrchestrator.java` — TU ARCHIVO OBJETIVO
  Actualmente devuelve un `CritiqueResult` con valores fijos.
  Inyecta `LlmClient` por constructor:
  `public CriticOrchestrator(LlmClient llmClient)`

---

## Estado del proyecto
- `mvn clean compile` → SUCCESS
- `mvn test` → 29/29 pasando

---

## Tu tarea

Implementa `CriticOrchestrator.java` — un método: `critique()`

---

### Constante del system prompt

```java
private static final String CRITIQUE_SYSTEM_PROMPT = """
You are a product and architecture auditor. Your job is to analyze three
documents — context, flows, and tasks — produced by a software design pipeline
and find:
- **critiques**: specific problems found (contradictions, vague statements,
  missing detail, unrealistic assumptions)
- **gaps**: what is missing entirely (missing flows, missing user roles,
  missing error handling, missing integrations)
- **refinementNeeds**: specific topics that should go back to debate
  (unresolved trade-offs, risky assumptions, unclear scope boundaries)

You are not negative — you are rigorous and constructive.
Every finding must be specific and actionable.

Respond ONLY in JSON with this exact format, no preamble, no markdown:
{
  "critiques": ["critique 1", "critique 2"],
  "gaps": ["gap 1"],
  "refinementNeeds": ["topic to re-debate 1"]
}

Each array should have 3-7 items. Do NOT return empty arrays —
if you truly find nothing, include at least one item acknowledging
the documents are solid in that area.
""";
```

---

## Implementación: `critique()`

**Firma:**
```java
public CritiqueResult critique(PipelineOutput docs, List<DebateMessage> debateHistory)
```

**Propósito:** Auditar los tres documentos sintetizados contra el historial de debate
y devolver una crítica estructurada con vacíos y necesidades de refinamiento.

---

### Pasos de implementación

1. **Validar entradas**
   - Si `docs == null`: lanzar `PipelineException("docs is null", "Phase3a", 0)`
   - Si `debateHistory == null`: lanzar `PipelineException("debateHistory is null", "Phase3a", 0)`

2. **Construir el mensaje de usuario**

   Formatea los tres documentos y el resumen del debate:

   ```
   --- CONTEXT DOCUMENT ---
   [docs.contextDocument()]

   --- FLOW DOCUMENT ---
   [docs.flowDocument()]

   --- TASK DOCUMENT ---
   [docs.taskDocument()]

   --- DEBATE HISTORY (summary) ---
   [agentName] (round N): [content]
   ...
   ```

   - Si `debateHistory` está vacío, escribe: `"(No debate history available)"`
   - Formatea cada `DebateMessage` como: `[agentName] (round N): [content]`
   - ⚠️ **Presupuesto de tokens**: Si `debateHistory.size() > 30`, incluye solo:
     - Los primeros 5 mensajes
     - Los últimos 10 mensajes
     - Marca el truncamiento: `... (N messages omitted) ...`

3. **Llamar al LLM**
   ```java
   String response = llmClient.chat(CRITIQUE_SYSTEM_PROMPT, userMessage);
   ```

4. **Validar la respuesta**
   - Si `response == null || response.isBlank()`:
     lanzar `PipelineException("Empty response from LLM", "Phase3a", 0)`

5. **Parsear el JSON**
   ```java
   try {
       JsonNode node = JsonParser.parse(response);
       List<String> critiques = extractStringList(node, "critiques");
       List<String> gaps = extractStringList(node, "gaps");
       List<String> refinementNeeds = extractStringList(node, "refinementNeeds");
       return new CritiqueResult(critiques, gaps, refinementNeeds);
   } catch (RuntimeException e) {
       throw new PipelineException("Failed to parse critique JSON", "Phase3a", 0, e);
   }
   ```

6. **Método auxiliar de extracción**
   ```java
   private List<String> extractStringList(JsonNode node, String fieldName) {
       JsonNode array = node.get(fieldName);
       if (array == null || !array.isArray()) {
           log.warn("Field '{}' missing or not an array, defaulting to empty list", fieldName);
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

- `log.info()` al entrar al método: longitudes de los documentos y tamaño de debateHistory
- `log.info()` tras el éxito: recuentos de critiques, gaps, refinementNeeds
- `log.warn()` si debateHistory está vacío
- `log.warn()` si debateHistory se truncó por el presupuesto de tokens
- `log.warn()` si falta cualquier campo JSON (fallback a lista vacía)
- `log.error()` en cualquier excepción capturada antes de relanzarla

---

## Manejo de excepciones

```java
// Patrón para todos los fallos de LLM/parseo:
try {
    // ... llamada al LLM + parseo JSON ...
} catch (PipelineException e) {
    throw e;  // relanzar tal cual
} catch (RuntimeException e) {
    throw new PipelineException("Critique failed", "Phase3a", 0, e);
}
```

---

## Estándares de código

- `@Service` + `@Slf4j` en la clase
- Solo inyección por constructor — `public CriticOrchestrator(LlmClient llmClient)`
- Sin `@Autowired` en campos
- El system prompt como constante `private static final String`
- Todo el parseo JSON a través de `JsonParser.parse()`
- Todas las excepciones del LLM envueltas en `PipelineException`
- Sin cadenas de crítica hardcodeadas — toda la salida debe venir del LLM
- Elimina toda la lógica de marcador de posición hardcodeada (`Arrays.asList(...)` etc.)

---

## Requisitos de pruebas (TDD)

Archivo de pruebas: `src/test/java/com/ideapipeline/orchestrator/CriticOrchestratorTest.java`
Usa `@ExtendWith(MockitoExtension.class)` y mockea `LlmClient`.

### Pruebas requeridas

1. **`critique_shouldReturnParsedCritiqueResult`**
   - Mockea `llmClient.chat()` para devolver:
     ```json
     {"critiques":["c1","c2"],"gaps":["g1"],"refinementNeeds":["r1","r2"]}
     ```
   - Verifica que el resultado tiene 2 critiques, 1 gap, 2 refinementNeeds

2. **`critique_shouldThrowPipelineException_onInvalidJson`**
   - Mockea `llmClient.chat()` para devolver `"not valid json"`
   - Verifica que se lanza `PipelineException` con fase `"Phase3a"`

3. **`critique_shouldThrowPipelineException_onEmptyResponse`**
   - Mockea `llmClient.chat()` para devolver `""`
   - Verifica que se lanza `PipelineException` con fase `"Phase3a"`

4. **`critique_shouldHandleMarkdownFencedResponse`**
   - Mockea `llmClient.chat()` para devolver:
     ````
     ```json
     {"critiques":["c1"],"gaps":[],"refinementNeeds":[]}
     ```
     ````
   - Verifica que `JsonParser` elimina las vallas correctamente y que el resultado tiene 1 critique

5. **`critique_shouldIncludeAllThreeDocumentsInPrompt`**
   - Usa `ArgumentCaptor<String>` para capturar el `userMessage`
   - Verifica que contiene el contenido de `contextDocument`, `flowDocument` y `taskDocument`

6. **`critique_shouldFormatDebateHistoryInUserMessage`**
   - Proporciona un debateHistory con 2 mensajes
   - Verifica que `userMessage` contiene `[agentName] (round N): [content]` para cada uno

7. **`critique_shouldHandleEmptyDebateHistory`**
   - Pasa `List.of()` como debateHistory
   - Verifica que no se lanza excepción y que userMessage contiene `"No debate history available"`

8. **`critique_shouldHandleMissingJsonFields`**
   - Mockea `llmClient.chat()` para devolver `{"critiques":["c1"]}` (faltan gaps y refinementNeeds)
   - Verifica que el resultado tiene `critiques=["c1"]`, `gaps=[]`, `refinementNeeds=[]`

9. **`critique_shouldThrowPipelineException_onLlmFailure`**
   - Mockea `llmClient.chat()` para lanzar `RuntimeException("LLM error")`
   - Verifica que se lanza `PipelineException` con fase `"Phase3a"`

---

## Criterios de aceptación

1. `mvn clean compile` → SUCCESS
2. `mvn test` → 9 pruebas nuevas pasan (CriticOrchestratorTest)
3. Total de pruebas ≥ 38 pasando
4. `critique()` NO tiene datos hardcodeados — toda la salida viene del LLM
5. Los campos de `CritiqueResult` se rellenan desde el JSON parseado
6. Respuestas nulas/vacías del LLM lanzan `PipelineException`
7. La RuntimeException de `JsonParser` se envuelve en `PipelineException`
8. El historial de debate se formatea como `[agentName] (round N): [content]`
9. Manejo del presupuesto de tokens para historiales de debate grandes (truncamiento a partir de 30 mensajes)
10. Los tres documentos (contexto, flujo, tareas) aparecen en el prompt del LLM
11. El system prompt se define como constante `private static final String`

---

## Archivos a modificar

1. **`src/main/java/com/ideapipeline/orchestrator/CriticOrchestrator.java`**
   - Inyectar `LlmClient` por constructor
   - Reemplazar el marcador de posición hardcodeado por la implementación basada en LLM
   - Añadir el método auxiliar `extractStringList()`

2. **`src/test/java/com/ideapipeline/orchestrator/CriticOrchestratorTest.java`**
   - Crear un archivo de pruebas nuevo (o reemplazar el existente si hay uno)
   - 9 métodos de prueba según lo especificado arriba

---

## Criterios de éxito

Tras la implementación:
- ✅ El código compila sin advertencias
- ✅ Las 9 pruebas pasan
- ✅ No quedan cadenas de crítica hardcodeadas
- ✅ Manejo de excepciones adecuado en todo el código
- ✅ Logging en los niveles apropiados

---

Fin de phase-3a-critique.prompt.md v1.0
