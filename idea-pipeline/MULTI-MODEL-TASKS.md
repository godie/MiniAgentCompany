# MULTI-MODEL-TASKS.md — Tareas de Implementación para Agentes de Código

**Versión:** 1.0
**Fecha:** 2026-04-19
**Basado en:** `MULTI-MODEL-SPEC.md` v1.1
**Estado:** LISTO PARA EJECUTAR

---

## Contextos requeridos para leer ANTES de implementar

Todos los agentes deben leer estos archivos antes de escribir código:

```
idea-pipeline/MULTI-MODEL-SPEC.md
idea-pipeline/META_PIPELINE.md
idea-pipeline/CONTEXT.md
idea-pipeline/AGENTS.md
```

---

## FASES DE IMPLEMENTACIÓN

---

## Fase 1: Core — Factory + Ollama (Semana 1)

### Tarea 1.1: Crear Enum PipelinePhase

**Archivo:** `idea-pipeline/src/main/java/com/ideapipeline/model/enums/PipelinePhase.java`

```java
public enum PipelinePhase {
    GATEKEEPER,
    DEBATE,
    SYNTHESIS,
    CRITIC,
    VALIDATION,
    STACK_ARCHITECT,
    PLANNING_POKER
}
```

**Tests:** `src/test/java/com/ideapipeline/model/enums/PipelinePhaseTest.java`

---

### Tarea 1.2: Crear Enum LlmProvider

**Archivo:** `idea-pipeline/src/main/java/com/ideapipeline/model/enums/LlmProvider.java`

```java
public enum LlmProvider {
    OLLAMA,
    OPENROUTER,
    OPENCODE
}
```

**Tests:** `src/test/java/com/ideapipeline/model/enums/LlmProviderTest.java`

---

### Tarea 1.3: Crear Records de Configuración

**Archivos:**

1. `idea-pipeline/src/main/java/com/ideapipeline/config/LlmProperties.java`
   - `@ConfigurationProperties(prefix = \"llm\")`
   - fields: `providers`, `defaultProvider`, `defaultModel`, `phases`, `fallbackChain`, `modelPrompts`, `complexity`

2. `idea-pipeline/src/main/java/com/ideapipeline/config/ProviderConfig.java`
   - record: `baseUrl`, `apiKey` (nullable)

3. `idea-pipeline/src/main/java/com/ideapipeline/config/PhaseConfig.java`
   - record: `provider`, `model`

4. `idea-pipeline/src/main/java/com/ideapipeline/config/ModelPromptConfig.java`
   - record: `systemPrefix`, `temperatureOverride`, `maxTokensOverride`

5. `idea-pipeline/src/main/java/com/ideapipeline/config/ComplexityConfig.java`
   - record: `thresholdModerate`, `thresholdComplex`, `moderateModel`, `complexModel`

**Tests:** `src/test/java/com/ideapipeline/config/LlmPropertiesTest.java`

---

### Tarea 1.4: Crear LlmProviderFactory

**Archivo:** `idea-pipeline/src/main/java/com/ideapipeline/config/LlmProviderFactory.java`

**Responsabilidades:**
- Crear `ChatModel` instances (cachadas por provider+model)
- Crear `LlmClient` por fase con provider ya resuelto
- Soportar fallback chain

**Código clave (verificado contra Spring AI source):**

```java
@Configuration
public class LlmProviderFactory {
    
    private final LlmProperties props;
    private final Map<String, ChatModel> chatModels = new HashMap<>();
    
    public LlmClient createClientForPhase(PipelinePhase phase) {
        PhaseConfig phaseConfig = resolveConfig(phase);
        ChatModel primary = getOrCreateChatModel(phaseConfig.provider(), phaseConfig.model());
        List<ChatModel> fallbacks = buildFallbackChain();
        return new LlmClientImpl(primary, fallbacks, phase, phaseConfig);
    }
    
    private ChatModel getOrCreateChatModel(LlmProvider provider, String model) {
        String key = provider.name() + ':' + model;
        return chatModels.computeIfAbsent(key, k -> {
            return switch (provider) {
                case OLLAMA -> createOllamaChatModel(props.getProviders().get(\"ollama\").baseUrl(), model);
                case OPENROUTER -> createOpenAiChatModel(
                    props.getProviders().get(\"openrouter\").baseUrl(),
                    props.getProviders().get(\"openrouter\").apiKey(),
                    model
                );
                case OPENCODE -> createOpenAiChatModel(
                    props.getProviders().get(\"opencode\").baseUrl(),
                    props.getProviders().get(\"opencode\").apiKey(),
                    model
                );
            };
        });
    }
    
    private OllamaChatModel createOllamaChatModel(String baseUrl, String model) {
        OllamaApi api = new OllamaApi(baseUrl);
        OllamaChatOptions options = OllamaChatOptions.builder().model(model).build();
        return OllamaChatModel.builder()
            .ollamaApi(api)
            .defaultOptions(options)
            .observationRegistry(ObservationRegistry.NOOP)
            .modelManagementOptions(ModelManagementOptions.defaults())
            .build();
    }
    
    private OpenAiChatModel createOpenAiChatModel(String baseUrl, String apiKey, String model) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .model(model)
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .build();
        return OpenAiChatModel.builder()
            .options(options)
            .observationRegistry(ObservationRegistry.NOOP)
            .build();
    }
    
    private List<ChatModel> buildFallbackChain() {
        // PLACEHOLDER - Implementar en Fase 2 cuando se defina la config de fallback-chain en YAML
        // Por ahora retorna lista vacía — el fallback chain está definido pero no se usa en Fase 1
        return List.of();
    }
}
```

**Imports necesarios:**
```java
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.ollama.management.ModelManagementOptions;
```

**Tests:** `src/test/java/com/ideapipeline/config/LlmProviderFactoryTest.java`
- Test: factory crea OllamaChatModel con el modelo correcto
- Test: factory cachea ChatModel instances (no recrea)
- Test: createClientForPhase devuelve LlmClient con fase correcta

---

### Tarea 1.5: Actualizar LlmClientImpl

**Archivo:** `idea-pipeline/src/main/java/com/ideapipeline/client/LlmClientImpl.java`

**Cambios:**
- Remover `@Service` annotation (será creado por factory)
- Aceptar `ChatModel` primary + `List<ChatModel> fallbacks` + `PipelinePhase` + `PhaseConfig`
- Implementar fallback chain en métodos `chat()` y `chatWithHistory()`
- Logging estructurado: phase, provider, model, duration

**Estructura:**
```java
public class LlmClientImpl implements LlmClient {
    private final ChatModel primaryChatModel;
    private final List<ChatModel> fallbackChatModels;
    private final PipelinePhase phase;
    private final PhaseConfig phaseConfig;
    
    @Override
    public String chat(String systemPrompt, String userMessage) {
        List<ChatModel> tried = new ArrayList<>();
        Exception lastError = null;
        
        // Try primary
        try {
            return callChatModel(primaryChatModel, systemPrompt, userMessage);
        } catch (Exception e) {
            log.warn(...);
            tried.add(primaryChatModel);
            lastError = e;
        }
        
        // Try fallbacks
        for (ChatModel fallback : fallbackChatModels) {
            try {
                return callChatModel(fallback, systemPrompt, userMessage);
            } catch (Exception e) {
                log.warn(...);
                tried.add(fallback);
                lastError = e;
            }
        }
        
        // All failed
        throw new PipelineException(
            String.format(\"All LLM providers failed for phase %s. Tried: %s. Last error: %s\",
                phase, tried.stream().map(m -> m.getClass().getSimpleName()).toList(), lastError.getMessage()),
            phase.name(),
            0
        );
    }
}
```

**Tests:** `src/test/java/com/ideapipeline/client/LlmClientImplFallbackTest.java`
- Test: fallback chain funciona cuando primary falla
- Test: success directo cuando primary funciona
- Test: PipelineException cuando todos fallan

---

### Tarea 1.6: Quitar @Service de todos los Orquestadores ⚠️ IMPORTANTE

**ESTA TAREA DEBE COMPLETARSE ANTES DE LA 1.7**

**Archivos a modificar (remover `@Service` annotation):**
- `src/main/java/com/ideapipeline/orchestrator/GatekeeperOrchestrator.java`
- `src/main/java/com/ideapipeline/orchestrator/DebateOrchestrator.java`
- `src/main/java/com/ideapipeline/orchestrator/SynthesisOrchestrator.java`
- `src/main/java/com/ideapipeline/orchestrator/CriticOrchestrator.java`
- `src/main/java/com/ideapipeline/orchestrator/ValidationOrchestrator.java`
- `src/main/java/com/ideapipeline/orchestrator/StackArchitectOrchestrator.java`
- `src/main/java/com/ideapipeline/orchestrator/ScrumMasterOrchestrator.java`

**También quitar @Service de:**
- `src/main/java/com/ideapipeline/client/LlmClientImpl.java`

**IdeaPipeline.java:** 
- OPCIÓN A: Mantener `@Service` en IdeaPipeline (no necesita factory, recibe orchestors por constructor)
- OPCIÓN B: Quitar `@Service` y crear @Bean en LlmProviderConfig
- **Recomendado: OPCIÓN A** — IdeaPipeline es el orquestador principal, no necesita factory

---

### Tarea 1.7: Crear LlmProviderConfig

**Archivo:** `idea-pipeline/src/main/java/com/ideapipeline/config/LlmProviderConfig.java`

**Responsabilidades:**
- Crear beans manualmente (deshabilita autoconfiguración)
- Wireup de todos los orquestadores con sus LlmClient

```java
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmProviderConfig {
    
    @Bean
    public LlmProviderFactory llmProviderFactory(LlmProperties props) {
        return new LlmProviderFactory(props);
    }
    
    // Wireup de orquestadores — @Service ya removido en Tarea 1.6
    @Bean
    public GatekeeperOrchestrator gatekeeperOrchestrator(LlmProviderFactory factory) {
        return new GatekeeperOrchestrator(factory.createClientForPhase(PipelinePhase.GATEKEEPER));
    }
    
    @Bean
    public DebateOrchestrator debateOrchestrator(LlmProviderFactory factory) {
        return new DebateOrchestrator(factory.createClientForPhase(PipelinePhase.DEBATE));
    }
    
    @Bean
    public SynthesisOrchestrator synthesisOrchestrator(LlmProviderFactory factory) {
        return new SynthesisOrchestrator(factory.createClientForPhase(PipelinePhase.SYNTHESIS));
    }
    
    @Bean
    public CriticOrchestrator criticOrchestrator(LlmProviderFactory factory) {
        return new CriticOrchestrator(factory.createClientForPhase(PipelinePhase.CRITIC));
    }
    
    @Bean
    public ValidationOrchestrator validationOrchestrator(LlmProviderFactory factory, PipelineProperties pipelineProperties) {
        return new ValidationOrchestrator(factory.createClientForPhase(PipelinePhase.VALIDATION), pipelineProperties);
    }
    
    @Bean
    public StackArchitectOrchestrator stackArchitectOrchestrator(LlmProviderFactory factory) {
        return new StackArchitectOrchestrator(factory.createClientForPhase(PipelinePhase.STACK_ARCHITECT));
    }
    
    @Bean
    public ScrumMasterOrchestrator scrumMasterOrchestrator(LlmProviderFactory factory) {
        return new ScrumMasterOrchestrator(factory.createClientForPhase(PipelinePhase.PLANNING_POKER));
    }
    
    // IdeaPipeline se mantiene como @Service, recibe orchestadores inyectados
}
```

---

### Tarea 1.8: Actualizar application.yml

**Archivo:** `idea-pipeline/src/main/resources/application.yml`

**Agregar al inicio:**
```yaml
spring:
  autoconfigure:
    exclude:
      # Ollama (verificado en spring-ai-autoconfigure-model-ollama)
      - org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration
      - org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration
      - org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration
      # OpenAI (verificado en spring-ai-autoconfigure-model-openai)
      - org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration
      - org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration
      - org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration
      - org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration
      - org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration
      - org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration

llm:
  providers:
    ollama:
      base-url: http://localhost:11434
    openrouter:
      base-url: https://openrouter.ai/api/v1
      api-key: ${OPENROUTER_API_KEY:}
    opencode:
      base-url: http://localhost:8080
      api-key: ${OPENCODE_API_KEY:}

  default-provider: ollama
  default-model: qwen3.5:4b
  
  phases:
    gatekeeper:
      provider: ollama
      model: qwen3.5:4b
    debate:
      provider: ollama
      model: llama3.2
    synthesis:
      provider: ollama
      model: qwen3.5:4b
    critic:
      provider: openrouter
      model: deepseek/deepseek-chat-v3
    validation:
      provider: openrouter
      model: anthropic/claude-3.5-sonnet
    stack-architect:
      provider: openrouter
      model: anthropic/claude-3.5-sonnet
    planning-poker:
      provider: ollama
      model: qwen3.5:4b

  fallback-chain:
    - provider: openrouter
      model: deepseek/deepseek-chat-v3
```

---

### Tarea 1.9: Actualizar pom.xml

**Archivo:** `idea-pipeline/pom.xml`

**PASO 1: Verificar si dependencias ya están en BOM**

```bash
# Revisar si spring-ai-bom ya define las versiones
grep -A5 "spring-ai-bom" idea-pipeline/pom.xml
```

Si el BOM ya está importado, agregar los modules directamente sin versión:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**PASO 2: Agregar dependencias sin versión (version de BOM)**

```xml
<!-- Spring AI Ollama -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-ollama</artifactId>
</dependency>

<!-- Spring AI OpenAI (para OpenRouter, DeepSeek, Groq) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai</artifactId>
</dependency>
```

**PASO 3: Verificar que NO hay starters conflictivos**

Buscar y remover si existen:
```xml
<!-- REMOVER si existe - causa conflictos de autoconfig -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-ollama</artifactId>
</dependency>
```

---

### Tarea 1.10: Tests de Integración

**Tests de wiring:**
- `src/test/java/com/ideapipeline/config/LlmProviderConfigTest.java`

**Casos de prueba específicos:**

```java
class LlmProviderConfigTest {
    
    @Test
    void allBeansCreatedWithoutConflicts(ApplicationContext ctx) {
        // Verifica que no hay múltiples ChatModel beans sin @Primary
        // Si hay conflicto, Spring lanza NoUniqueBeanDefinitionException
        ctx.getBeansOfType(ChatModel.class);
    }
    
    @Test
    void gatekeeperOrchestratorHasCorrectPhase(GatekeeperOrchestrator orch) {
        // Verifica que el LlmClient del orquestador tiene fase GATEKEEPER
        // usando Reflection para obtener el campo privado o mock verify
    }
    
    @Test
    void eachOrchestratorHasDedicatedLlmClient() {
        // Verifica que CriticOrchestrator y ValidationOrchestrator
        // usan OpenRouter (verificado en config)
    }
    
    @Test
    void fallbackChainConfiguredGlobally() {
        // Verifica que fallbacks están configurados desde YAML
    }
}
```

**Tests existentes:**
- Verificar que todos los tests existentes siguen pasando:
  ```bash
  cd idea-pipeline && mvn clean test
  ```

---

## Fase 2: Multi-provider + Fallback (Semana 2)

### Tarea 2.1: Verificar OpenRouter funciona

- Test con `OpenAiChatModel` + `baseUrl: https://openrouter.ai/api/v1`
- Verificar que modelos como `deepseek/deepseek-chat-v3` funcionan

### Tarea 2.2: Implementar ModelPromptRegistry

**Archivo:** `idea-pipeline/src/main/java/com/ideapipeline/config/ModelPromptRegistry.java`

```java
@Component
public class ModelPromptRegistry {
    
    private final Map<String, ModelPromptConfig> prompts;
    
    public String applyPrefix(String model, String basePrompt) {
        ModelPromptConfig config = prompts.get(model);
        if (config != null && config.systemPrefix() != null) {
            return config.systemPrefix() + basePrompt;
        }
        return basePrompt;
    }
}
```

### Tarea 2.3: Logging estructurado completo

- phase, provider, model, duration para cada llamada
- Fallback triggered con previous provider/model
- Todos fallaron con lista de modelos intentados

---

## Fase 3: Stats y Métricas (Semana 3)

### Tarea 3.1: Entity LlmCallStats

**Archivo:** `idea-pipeline/src/main/java/com/ideapipeline/model/LlmCallStats.java`

**Importante:** `tokensUsed` debe ser `Integer` (nullable) porque Ollama local no siempre retorna usage.

### Tarea 3.2: Repository

**Archivo:** `idea-pipeline/src/main/java/com/ideapipeline/repository/LlmCallStatsRepository.java`

---

## VALIDACIÓN FINAL

Después de completar Fase 1:

```bash
cd idea-pipeline && mvn clean compile
cd idea-pipeline && mvn test
```

**Verificar:**
1. `mvn clean compile` → SUCCESS
2. `mvn test` → Todos los tests pasan (existentes + nuevos)
3. No hay `@Service` en orquestadores (conflictos)
4. application.yml tiene las autoconfig exclusions

---

## Dependencias entre tareas

```
Tarea 1.1 → Tarea 1.4 (PipelinePhase usado en Factory)
Tarea 1.2 → Tarea 1.4 (LlmProvider usado en Factory)
Tarea 1.3 → Tarea 1.4 (LlmProperties usado en Factory)
Tarea 1.4 → Tarea 1.5 (LlmClientImpl recibe ChatModel)
Tarea 1.4 → Tarea 1.6 (Factory usado en Config)
Tarea 1.6 → Tarea 1.7 (quitar @Service primero)
Tarea 1.7 → Tarea 1.8 (actualizar YAML config)
Tarea 1.8 → Tarea 1.10 (tests de integración)
```

---

## Notas para agentes

1. **NO modificar la interfaz LlmClient** — los orquestadores no cambian
2. **Usar Builder pattern** — verificado contra Spring AI source code
3. **Remover @Service** de orquestadores ANTES de crear @Bean en config
4. **Autoconfigure exclusions** — deben estar en application.yml para evitar conflictos
5. **tokensUsed nullable** — obligatorio para compatibilidad con Ollama local

---

*Fin de tareas.*