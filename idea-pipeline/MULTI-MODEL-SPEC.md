# MULTI-MODEL-SPEC.md — Sistema Multi-Modelo para idea-pipeline

**Versión:** 1.1
**Fecha:** 2026-04-19
**Autor:** Design interview con el usuario
**Estado:** DRAFT
**Changelog v1.1:** Incorporado review de diseño — eliminado `chatForPhase()`,
OpenRouter/OpenCode reusan `OpenAiChatModel`, `ModelSelector` movido a Fase 3,
`tokensUsed` nullable, Spring AI autoconfigure deshabilitado, `LlmClient` por fase.

---

## 1. Resumen Ejecutivo

Agregar soporte para múltiples modelos LLM de diferentes proveedores (Ollama, OpenRouter, OpenCode) en idea-pipeline. Cada fase/orquestador puede usar un modelo diferente, con fallback automático entre proveedores y configuración via YAML.

**Proveedores soportados:**
- Ollama (modelos locales: qwen3.5, llama3, deepseek-r1, codellama)
- OpenRouter (Claude, Gemini, Mistral, etc.)
- OpenCode/Continue (mix local + remote)

---

## 2. Arquitectura del Sistema

### 2.1 Patrón de Diseño: Strategy + Factory

**Principio clave:** Cada orquestador recibe su propio `LlmClient` pre-configurado
para su fase. Los orquestadores NO conocen el provider ni el modelo — solo llaman
`chat()` y `chatWithHistory()`. El routing ocurre en startup, no en runtime.

```
┌─────────────────────────────────────────────────────────────────┐
│                    LlmProviderFactory                           │
│  - Crea ChatModel beans según config YAML                       │
│  - Crea LlmClient por fase con provider ya resuelto            │
│  - Fallback chain se resuelve en startup                       │
└─────────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
   │  OllamaApi   │    │ OpenAiApi   │    │ OpenAiApi   │
   │  (local)     │    │(OpenRouter) │    │ (OpenCode)  │
   └─────────────┘    └─────────────┘    └─────────────┘
          │                   │                   │
          ▼                   ▼                   ▼
   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
   │OllamaChatModel│   │OpenAiChatModel│   │OpenAiChatModel│
   │  (qwen3.5)   │    │(deepseek-r1) │    │(codellama)   │
   └─────────────┘    └─────────────┘    └─────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
   │LlmClientImpl │    │LlmClientImpl │    │LlmClientImpl │
   │ (gatekeeper) │    │  (critic)    │    │(planning-pkr)│
   └─────────────┘    └─────────────┘    └─────────────┘
          │                   │                   │
          ▼                   ▼                   ▼
   GatekeeperOrch.    CriticOrchestr.   ScrumMasterOrch.
```

**Wireup en `@Configuration`:**
```java
@Bean
public CriticOrchestrator criticOrchestrator(LlmProviderFactory factory) {
    LlmClient client = factory.createClientForPhase(PipelinePhase.CRITIC);
    return new CriticOrchestrator(client);
}
```

### 2.2 Componentes a crear

| Componente | Responsabilidad | Patrón |
|------------|-----------------|--------|
| `PipelinePhase` | Enum de fases del pipeline | Enum |
| `LlmProviderFactory` | Crea `ChatModel` beans y `LlmClient` por fase | Factory |
| `LlmClientImpl` | Wrapper de `ChatClient` con fallback y stats | Component |
| `LlmCallStats` | Entity para persistir estadísticas | Entity JPA |
| `ModelSelector` | Selecciona modelo según complejidad (Fase 3) | Strategy |

**Nota:** No hay providers custom. OpenRouter y OpenCode son OpenAI-compatible.
Se reusan `OllamaChatModel` y `OpenAiChatModel` de Spring AI con base-url custom.

---

## 3. Configuración

### 3.1 Estructura en application.yml

```yaml
spring:
  # DESHABILITAR auto-configuración conflictiva de Spring AI.
  # Con múltiples starters (ollama + openai) en classpath, Spring
  # intenta crear múltiples ChatModel beans y falla. Creamos los
  # beans manualmente en LlmProviderConfig.
  autoconfigure:
    exclude:
      - org.springframework.ai.autoconfigure.ollama.OllamaAutoConfiguration
      - org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration

llm:
  providers:
    ollama:
      base-url: http://localhost:11434
    openrouter:
      base-url: https://openrouter.ai/api/v1
      api-key: ${OPENROUTER_API_KEY}
    opencode:
      base-url: http://localhost:8080   # o la URL de tu instancia
      api-key: ${OPENCODE_API_KEY}

  default-provider: ollama
  default-model: qwen3.5:4b
  
  # Configuración por fase — provider + modelo
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
  
  # Fallback chain — se intenta en orden si el provider principal falla
  fallback-chain:
    - provider: openrouter
      model: deepseek/deepseek-chat-v3
    - provider: opencode
      model: codellama:7b
  
  # Prompts por modelo — prefijo que se concatena al system prompt de fase
  model-prompts:
    qwen3.5:4b:
      system-prefix: |
        You are a helpful assistant. Be concise and direct.
    deepseek/deepseek-chat-v3:
      system-prefix: |
        You are a reasoning model. Think step by step before answering.
    anthropic/claude-3.5-sonnet:
      system-prefix: |
        You are Claude, an AI assistant by Anthropic. Be precise and thorough.

  # Config de complejidad para selección dinámica (Fase 3 del roadmap)
  # NO implementar en Fase 1 — es nice-to-have
  complexity:
    threshold-moderate: 500  # chars de idea
    threshold-complex: 2000
    moderate-model: qwen3.5:4b
    complex-model: deepseek/deepseek-chat-v3
```

### 3.2 Credentials via Environment Variables

```bash
export OLLAMA_API_KEY=ollama-local
export OPENROUTER_API_KEY=sk-or-v1-xxxxx
export OPENCODE_API_KEY=local-dev
```

---

## 4. Modelo de Datos

### 4.0 Config Records (agregados del review)

```java
// 4.0.1 LlmProperties — mapea la sección 'llm:' del YAML
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
    Map<String, ProviderConfig> providers,
    String defaultProvider,
    String defaultModel,
    Map<String, PhaseConfig> phases,
    List<PhaseConfig> fallbackChain,  // Global fallback
    Map<String, ModelPromptConfig> modelPrompts,
    ComplexityConfig complexity
) {}

// 4.0.2 ProviderConfig — config por provider
public record ProviderConfig(
    String baseUrl,
    String apiKey  // Nullable para Ollama local
) {}

// 4.0.3 PhaseConfig — config por fase
public record PhaseConfig(
    String provider,  // ollama, openrouter, opencode
    String model      // qwen3.5:4b, deepseek-r1, etc.
) {}

// 4.0.4 ModelPromptConfig — prompts por modelo
public record ModelPromptConfig(
    String systemPrefix,
    Double temperatureOverride,
    Integer maxTokensOverride
) {}

// 4.0.5 ComplexityConfig — para selección dinámica (Fase 3)
public record ComplexityConfig(
    int thresholdModerate,
    int thresholdComplex,
    String moderateModel,
    String complexModel
) {}
```

### 4.1 Entity: LlmCallStats

```java
@Entity
@Table(name = llm_call_stats)
public class LlmCallStats {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    private String phase;           // gatekeeper, debate, synthesis, etc.
    private String provider;        // ollama, openrouter, opencode
    private String model;           // qwen3.5:4b, claude-3.5-sonnet
    private Integer tokensUsed;  // Nullable — Ollama local no siempre retorna usage
    private long durationMs;
    private boolean success;
    private String errorMessage;
    private String promptHash;      // hash del prompt para debugging
    private LocalDateTime createdAt;
}
```

### 4.2 Enum: LlmProvider

```java
public enum LlmProvider {
    OLLAMA,
    OPENROUTER,   // Reusa OpenAiChatModel con base-url custom
    OPENCODE       // Reusa OpenAiChatModel con base-url custom
}
```

**Nota:** `OPENROUTER` y `OPENCODE` NO necesitan implementación custom.
Ambos usan `OpenAiChatModel` de Spring AI con `base-url` apuntando
al endpoint correcto y `api-key` con la credencial del proveedor.

### 4.3 Enum: PipelinePhase

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

---

## 5. LlmProviderFactory — Creación de beans

**No hay interfaz `LlmProvider`.** Spring AI ya provee `ChatModel` como
abstracción común. El factory crea instancias de `OllamaChatModel` o
`OpenAiChatModel` según la config, y las envuelve en `LlmClientImpl`.

```java
@Configuration
public class LlmProviderConfig {

    @Bean
    public LlmProviderFactory llmProviderFactory(LlmProperties props) {
        // Crea y cachea ChatModel instances por (provider, model)
        return new LlmProviderFactory(props);
    }

    // Un LlmClient por orquestador — inyectado directamente
    @Bean @Qualifier("gatekeeperClient")
    public LlmClient gatekeeperClient(LlmProviderFactory factory) {
        return factory.createClientForPhase(PipelinePhase.GATEKEEPER);
    }

    @Bean @Qualifier("debateClient")
    public LlmClient debateClient(LlmProviderFactory factory) {
        return factory.createClientForPhase(PipelinePhase.DEBATE);
    }

    // ... etc para cada fase
}
```

### 5.1 LlmProviderFactory internals (CORREGIDO — usa Builder pattern verificado)

```java
public class LlmProviderFactory {
    private final Map<String, ChatModel> chatModels = new HashMap<>();

    public LlmClient createClientForPhase(PipelinePhase phase) {
        PhaseConfig phaseConfig = resolveConfig(phase);
        ChatModel primary = getOrCreateChatModel(phaseConfig.provider(), phaseConfig.model());
        List<ChatModel> fallbacks = buildFallbackChain();
        return new LlmClientImpl(primary, fallbacks, phase, phaseConfig);
    }

    private ChatModel getOrCreateChatModel(LlmProvider provider, String model) {
        String key = provider.name() + ":" + model;
        return chatModels.computeIfAbsent(key, k -> {
            return switch (provider) {
                case OLLAMA -> createOllamaChatModel(props.getProviders().getOllama().getBaseUrl(), model);
                case OPENROUTER -> createOpenAiChatModel(
                    props.getProviders().getOpenrouter().getBaseUrl(),
                    props.getProviders().getOpenrouter().getApiKey(),
                    model
                );
                case OPENCODE -> createOpenAiChatModel(
                    props.getProviders().getOpencode().getBaseUrl(),
                    props.getProviders().getOpencode().getApiKey(),
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
}
```

---

## 6. Lógica de Fallback

### 6.1 Flujo

```
1. `LlmClientImpl` ya tiene su fase y `ChatModel` primario (inyectados por factory en startup)
2. Intentar llamada con `primaryChatModel`
3. Si falla:
   a. Loguear error con phase, provider, model, duración
   b. Persistir stats en BD (solo si `statsRepository` disponible — Phase 3+)
   c. Obtener siguiente `ChatModel` del fallback chain
   d. Intentar de nuevo
   e. Repetir hasta agotar chain
4. Si todos fallan: lanzar PipelineException
```

### 6.2 PipelineException para fallo

```java
throw new PipelineException(
    String.format(
        "All LLM providers failed for phase %s. Tried: %s. Last error: %s",
        phase, modelsTried, lastError.getMessage()
    ),
    phase.name(),  // PipelinePhase → String
    0
);
```

---

## 7. Model Selector (Selección Dinámica) — DEFERRED a Fase 3

**⚠️ No implementar en Fase 1.** La selección por complejidad basada en
longitud de texto es frágil: una idea de 300 chars puede ser enormemente
compleja ("sistema de trading de alta frecuencia con ML") y una de 2000
puede ser trivial. Si se implementa, que sea un feature nice-to-have
que NO afecte el routing crítico.

### 7.1 Lógica de complejidad (placeholder para Fase 3)

```java
// FASE 3 — Nice-to-have, no es core del sistema
public class ModelSelector {
    
    public LlmModelConfig selectModel(PipelinePhase phase, String idea, List<RefinementQA> refinements) {
        int totalLength = idea.length() + 
            refinements.stream().mapToInt(r -> r.question().length() + r.answer().length()).sum();
        
        if (totalLength > complexityThresholdComplex) {
            return LlmModelConfig.of(LlmProvider.OPENROUTER, config.getComplexModel());
        } else if (totalLength > complexityThresholdModerate) {
            return getPhaseConfig(phase);
        } else {
            return LlmModelConfig.of(config.getDefaultProvider(), config.getDefaultModel());
        }
    }
}
```

---

## 8. Logging

### 8.1 Niveles de log por evento

| Evento | Level | Contenido |
|--------|-------|-----------|
| Inicio de llamada | INFO | phase, provider, model, promptLength |
| Fin exitoso | INFO | phase, provider, model, duration, tokens |
| Fallback triggered | WARN | phase, previousProvider, previousModel, error |
| Fallback success | INFO | phase, finalProvider, finalModel, totalDuration |
| Todos fallaron | ERROR | phase, allModelsTried, lastError |
| Stats persistidos | DEBUG | entityId, duración |

### 8.2 Formato de log

```
INFO  [idea-pipeline] [llm] phase=critic provider=openrouter model=deepseek-r1 duration=1234ms tokens=512
WARN  [idea-pipeline] [llm] phase=critic fallback_triggered from=ollama/qwen3.5 reason=Connection timeout
INFO  [idea-pipeline] [llm] phase=critic fallback_success to=openrouter/deepseek-r1 total_duration=2345ms
ERROR [idea-pipeline] [llm] phase=critic all_providers_failed tried=[ollama/qwen3.5, openrouter/deepseek-r1, opencode/codellama] last_error=Rate limit exceeded
```

---

## 9. Cambios en Orchestrators

### 9.1 Principio: Orchestrators SIN cambios en lógica ni interfaz

Los orquestadores existentes NO necesitan cambios en su lógica ni en
su interfaz `LlmClient`. Cada orquestador recibe un `LlmClient`
pre-configurado para su fase vía `@Qualifier`:

```java
// CriticOrchestrator.java - SIN CAMBIOS en su código
public class CriticOrchestrator {
    private final LlmClient llmClient;  // Misma interfaz, misma firma
    
    public CritiqueResult critique(PipelineOutput docs, List<DebateMessage> history) {
        // llmClient ya está configurado con el provider/model de 'critic'
        String response = llmClient.chat(CRITIC_SYSTEM_PROMPT, buildUserMessage(docs, history));
        return parseResponse(response);
    }
}
```

### 9.2 LlmClient — interfaz SIN cambios

```java
// La interfaz NO cambia. No hay chatForPhase() ni chatWithHistoryForPhase().
// El routing está en el factory, no en la interfaz.
public interface LlmClient {
    String chat(String systemPrompt, String userMessage);
    String chatWithHistory(String systemPrompt, List<DebateMessage> history, String userMessage);
}
```

### 9.3 LlmClientImpl — responsabilidades ampliadas

```java
// NOT @Service — created by LlmProviderFactory, not component-scanned.
// Must be excluded from component scanning or have @Service removed.
public class LlmClientImpl implements LlmClient {
    private final ChatModel primaryChatModel;
    private final List<ChatModel> fallbackChatModels;  // ordered
    private final PipelinePhase phase;  // Para logging y stats
    private final ModelPromptRegistry promptRegistry;
    private final LlmCallStatsRepository statsRepository;
    
    // LlmClientImpl conoce su fase (se la inyecta el factory)
    // pero NO expone esa información a los orchestrators.
    
    // statsRepository y promptRegistry son opcionales en Phase 1.
    // En Phase 1 solo se inyecta: primaryChatModel, fallbackChatModels, phase.
    // En Phase 3 se agregan: promptRegistry, statsRepository.
    
    @Override
    public String chat(String systemPrompt, String userMessage) {
        String effectivePrompt = (promptRegistry != null) 
            ? promptRegistry.applyPrefix(phase, systemPrompt) 
            : systemPrompt;
        // Intentar con primary, luego fallback chain
        // Persistir stats solo si statsRepository != null (Phase 3+)
    }
}
```

### 9.4 Wireup en @Configuration

**⚠️ Los orquestadores existentes tienen `@Service`.** Al definir `@Bean` methods
para ellos, Spring crearía dos instancias (una por component scan, otra por factory).
**Solución:** Quitar `@Service` de todos los orquestadores y crearlos vía `@Bean`.

```java
// En LlmProviderConfig.java (nuevo @Configuration)
// Remover @Service de todos los orchestrators antes de agregar estos beans.

@Bean
public CriticOrchestrator criticOrchestrator(LlmProviderFactory factory) {
    LlmClient client = factory.createClientForPhase(PipelinePhase.CRITIC);
    return new CriticOrchestrator(client);
}

@Bean
public ValidationOrchestrator validationOrchestrator(
        LlmProviderFactory factory, PipelineProperties pipelineProperties) {
    LlmClient client = factory.createClientForPhase(PipelinePhase.VALIDATION);
    return new ValidationOrchestrator(client, pipelineProperties);
}

// ... etc para cada orquestador. También remover @Service de IdeaPipeline
// y crearlo como @Bean con todos los orquestadores inyectados.
```

---

## 10. Prompts por Modelo

### 10.1 Estructura

```java
public class ModelPromptRegistry {
    
    Map<String, ModelPrompt> promptsByModel; // model -> prompt templates
    
    public String getPromptForModel(String model, String basePrompt) {
        ModelPrompt modelPrompt = promptsByModel.get(model);
        if (modelPrompt != null && modelPrompt.getSystem() != null) {
            return modelPrompt.getSystem() + basePrompt;
        }
        return basePrompt;
    }
}

@Data
public class ModelPrompt {
    private String model;
    private String systemPrefix;   // Se concatena antes del prompt de fase
    private Double temperatureOverride;
    private Integer maxTokensOverride;
}
```

### 10.2 Aplicación

`LlmClientImpl` aplica el system prefix antes de enviar al LLM,
buscando el modelo configurado para su fase:
- `qwen3.5:4b` → Agrega contexto de ser conciso y directo
- `deepseek/deepseek-chat-v3` → Agrega contexto de reasoning step-by-step
- `anthropic/claude-3.5-sonnet` → Agrega contexto de ser helpful y preciso

```java
// En LlmClientImpl
private String applyModelPrefix(String systemPrompt) {
    String prefix = promptRegistry.getPrefixForPhase(phase);
    return prefix != null ? prefix + systemPrompt : systemPrompt;
}
```

---

## 11. Stats y Métricas

### 11.1 Repository

```java
@Repository
public interface LlmCallStatsRepository extends JpaRepository<LlmCallStats, String> {
    
    List<LlmCallStats> findByPhase(String phase);
    List<LlmCallStats> findByProvider(String provider);
    List<LlmCallStats> findBySuccessFalse();
    
    @Query(
        value = 
        // Stats agregados por phase y provider
    )
    List<PhaseProviderStats> getAggregatedStats();
}
```

### 11.2 Endpoints opcionales para stats

```
GET /llm/stats                    # Stats generales
GET /llm/stats/phase/{phase}      # Stats por fase
GET /llm/stats/provider/{provider}# Stats por provider
GET /llm/stats/failures           # Solo fallos
```

---

## 12. Tests

### 12.1 Tests unitarios

| Test | Descripción | Fase |
|------|-------------|------|
| `LlmProviderFactoryTest` | Factory crea ChatModel y LlmClient por fase | 1 |
| `LlmClientImplFallbackTest` | Fallback chain funciona con mocks | 1 |
| `LlmClientImplExistingTest` | Tests existentes siguen pasando | 1 |
| `OpenRouterChatModelTest` | OpenAiChatModel con base-url custom funciona | 2 |
| `FallbackChainIntegrationTest` | Fallback real entre providers | 2 |
| `ModelPromptRegistryTest` | System prefix se aplica por modelo | 2 |
| `ModelSelectorTest` | Selección por complejidad (nice-to-have) | 3 |
| `LlmCallStatsRepositoryTest` | Stats se persisten, tokensUsed nullable | 3 |

### 12.2 Tests de integración

- Test completo del pipeline con fallback
- Test de selección dinámica de modelos
- Test de múltiples proveedores concurrently

---

## 13. Dependencias

### 13.1 Ya existentes (pom.xml)

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-ollama</artifactId>
</dependency>
```

### 13.2 Sin dependencias nuevas

No se necesitan starters adicionales. OpenRouter y OpenCode son
OpenAI-compatible, así que `spring-ai-starter-model-openai` ya los
soporta con solo cambiar `base-url` y `api-key`.

### 13.3 Autoconfigure — PROBLEMA Y SOLUCIÓN

⚠️ **Spring AI activa auto-configuración agresiva.** Si tienes
`spring-ai-starter-model-ollama` Y `spring-ai-starter-model-openai`
en el classpath, Spring intenta crear múltiples `ChatModel` beans
automáticamente y hay conflictos de wiring.

**Solución:** Deshabilitar las auto-configs y crear los beans
manualmente en `LlmProviderConfig`:

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
```

Esto permite control total sobre qué beans se crean, evitando
conflictos y permitiendo múltiples instancias del mismo tipo
con `@Qualifier`.

---

## 14. Roadmap de implementación

### Fase 1: Core — Factory + Ollama (Semana 1)
- [ ] Crear `PipelinePhase` enum
- [ ] Crear `LlmProperties` (config properties para la sección `llm:`)
- [ ] Crear `LlmProviderFactory` — crea `ChatModel` beans y `LlmClient` por fase
- [ ] Crear `LlmProviderConfig` (@Configuration) — deshabilitar autoconfigure, crear beans
- [ ] Actualizar `LlmClientImpl` — aceptar `ChatModel` + fallback chain + fase (sin @Service)
- [ ] Quitar `@Service` de todos los orquestadores — crearlos vía `@Bean` en config
- [ ] Wireup: cada orquestador recibe su `LlmClient` vía factory
- [ ] Configuración básica en YAML (providers + phases + default)
- [ ] Tests: factory, client con fallback, wiring por fase
- [ ] Verificar que todos los tests existentes siguen pasando

### Fase 2: Multi-provider + Fallback (Semana 2)
- [ ] Agregar soporte OpenRouter (OpenAiChatModel con base-url custom)
- [ ] Agregar soporte OpenCode (OpenAiChatModel con base-url custom)
- [ ] Lógica de fallback chain en `LlmClientImpl`
- [ ] Logging estructurado (phase, provider, model, duration)
- [ ] `ModelPromptRegistry` — system prefix por modelo
- [ ] Tests: fallback chain, OpenRouter config, prompt registry

### Fase 3: Avanzado (Semana 3)
- [ ] Entity `LlmCallStats` (tokensUsed nullable)
- [ ] Repository y stats endpoints
- [ ] `ModelSelector` con complejidad (NICE-TO-HAVE, no core)
- [ ] Tests de integración end-to-end

### Fase 4: Testing y polish (Semana 4)
- [ ] Tests unitarios completos
- [ ] Tests de integración
- [ ] Documentación
- [ ] Demo end-to-end

---

## 15. edge cases y consideraciones

| Edge Case | Manejo |
|-----------|--------|
| Provider no responde (timeout) | Retry con backoff, luego fallback |
| Rate limiting | Backoff exponencial, fallback a otro provider |
| Modelo no existe en provider | Fallback, log de config error |
| Todas las credenciales faltantes | Fail con mensaje claro sobre vars de entorno |
| Prompt muy largo para modelo | Truncar con warning, o usar modelo con más contexto |
| Respuesta no es JSON válido | Log warning, retry con mismo modelo |

---

## 16. Decisiones resueltas (v1.1)

| Pregunta | Respuesta | Razón |
|----------|-----------|-------|
| ¿OpenRouter necesita provider custom? | **No** — reusa `OpenAiChatModel` | API 100% OpenAI-compatible |
| ¿OpenCode necesita provider custom? | **No** — reusa `OpenAiChatModel` | OpenAI-compatible |
| ¿`chatForPhase()` en `LlmClient`? | **No** — routing en factory | Orchestrators no cambian |
| ¿`ModelSelector` en Fase 1? | **No** — mover a Fase 3 | Longitud no implica complejidad |
| ¿Spring AI autoconfigure? | **Deshabilitar** — beans manuales | Evitar conflictos con múltiples starters |
| ¿`LlmClient` global o por fase? | **Por fase** — instancia dedicada | Más fácil testear, sin routing runtime |
| ¿`tokensUsed` siempre presente? | **No** — `Integer` nullable | Ollama local no siempre retorna usage |

## 17. API specifics de Spring AI — Details del código fuente

Basado en revisión de `/Users/diegomendozasalas/repos/spring-ai`:

### 17.1 OllamaChatModel — creación manual verificada ✅

```java
// Constructor requiere: OllamaApi, OllamaChatOptions, ToolCallingManager,
// ObservationRegistry, ModelManagementOptions
// PERO el Builder tiene defaults para todo excepto OllamaApi

OllamaApi ollamaApi = new OllamaApi("http://localhost:11434");

OllamaChatOptions options = OllamaChatOptions.builder()
    .model("qwen3.5:4b")
    .temperature(0.7)
    .build();

OllamaChatModel chatModel = OllamaChatModel.builder()
    .ollamaApi(ollamaApi)
    .defaultOptions(options)
    .observationRegistry(ObservationRegistry.NOOP)
    .modelManagementOptions(ModelManagementOptions.defaults())
    .build();
```

### 17.2 OpenAiChatModel — creación manual verificada ✅

```java
// OpenAiChatModel.builder() acepta:
// - options: OpenAiChatOptions (incluye baseUrl, apiKey, model)
// - openAiClient: OpenAIClient (opcional, se crea de options si null)
// - observationRegistry: ObservationRegistry (default NOOP)

OpenAiChatOptions options = OpenAiChatOptions.builder()
    .model("anthropic/claude-3.5-sonnet")
    .baseUrl("https://openrouter.ai/api/v1")
    .apiKey(System.getenv("OPENROUTER_API_KEY"))
    .temperature(0.7)
    .maxRetries(3)
    .build();

OpenAiChatModel chatModel = OpenAiChatModel.builder()
    .options(options)
    .observationRegistry(ObservationRegistry.NOOP)
    .build();
```

### 17.3 Clases de autoconfiguración exactas a excluir

```yaml
spring:
  autoconfigure:
    exclude:
      # Ollama
      - org.springframework.ai.model.ollama.autoconfigure.OllamaAutoConfiguration
      - org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration
      # OpenAI (OpenRouter, DeepSeek, Groq, etc. reusan esto)
      - org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration
      - org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration
      - org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration
      - org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration
      - org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration
```

### 17.4 URLs de providers verificadas

| Proveedor | Base URL | API compatible |
|-----------|----------|----------------|
| Ollama | `http://localhost:11434` | Custom (OllamaApi) |
| OpenRouter | `https://openrouter.ai/api/v1` | OpenAI ✅ |
| DeepSeek | `https://api.deepseek.com` | OpenAI ✅ |
| Groq | `https://api.groq.com/openai/v1` | OpenAI ✅ |

### 17.5 Modelos disponibles en Ollama (verificar con `ollama list`)

```bash
qwen3.5:4b      # Rápido, buena calidad,8B params
llama3.2:3b     # Moderado, 3B params
deepseek-r1:7b  # Razonamiento, 7B params
codellama:7b    # Código, 7B params
```

---

## 18. Preguntas abiertas

- [ ] Confirmar URL base de OpenCode API
- [x] **Decidir si el fallback chain es global o por fase** → **Global** por defecto, cada fase usa la misma fallback chain definida en `llm.fallback-chain`. Si una fase necesita fallback diferente, se puede override en `llm.phases.{phase}.fallbacks` (Fase 2+).
- [ ] Verificar que `ModelManagementOptions.defaults()` no intenta hacer pull automático del modelo

---

*Fin del spec.*