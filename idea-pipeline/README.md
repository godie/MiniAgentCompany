# idea-pipeline

Orquestador de pipeline para transformar ideas crudas en specs técnicas detalladas.

## Configuración de Modelos Multi-Provider

Este proyecto soporta los siguientes proveedores de LLM:

| Provider | Modelo por defecto | Variable de entorno |
|----------|-------------------|---------------------|
| **OpenAI** | `gpt-4o` | `OPENAI_API_KEY` |
| **Anthropic** | `claude-sonnet-4-20250514` | `ANTHROPIC_API_KEY` |
| **DeepSeek** | `deepseek-chat` | `DEEPSEEK_API_KEY` |
| **Mistral AI** | `mistral-large-latest` | `MISTRAL_AI_API_KEY` |
| **Google Gemini** | `gemini-2.0-flash` | `GOOGLE_GENAI_API_KEY` |
| **Ollama** | `llama3.2` | (local, no requiere API key) |

## Variables de entorno

### OpenAI

```bash
export OPENAI_API_KEY=sk-...
```

Opciones de configuración (opcionales):
```properties
spring.ai.openai.chat.options.model=gpt-4o
spring.ai.openai.chat.options.temperature=0.7
```

### Anthropic

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

Opciones de configuración (opcionales):
```properties
spring.ai.anthropic.chat.options.model=claude-sonnet-4-20250514
spring.ai.anthropic.chat.options.maxTokens=1024
```

### DeepSeek

```bash
export DEEPSEEK_API_KEY=sk-...
```

Opciones de configuración (opcionales):
```properties
spring.ai.deepseek.chat.options.model=deepseek-chat
spring.ai.deepseek.chat.options.temperature=0.7
```

### Mistral AI

```bash
export MISTRAL_AI_API_KEY=...
```

Opciones de configuración (opcionales):
```properties
spring.ai.mistralai.chat.options.model=mistral-large-latest
spring.ai.mistralai.chat.options.temperature=0.7
```

### Google Gemini

```bash
export GOOGLE_GENAI_API_KEY=...
```

Opciones de configuración (opcionales):
```properties
spring.ai.google.genai.chat.options.model=gemini-2.0-flash
spring.ai.google.genai.chat.options.temperature=0.7
```

### Ollama (local)

No requiere API key. Asegúrate de que Ollama esté corriendo localmente:

```bash
# Ver modelos disponibles
ollama list

# Si no tienes el modelo, instalar
ollama pull llama3.2
```

Configuración:
```properties
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=llama3.2
```

## Archivo `application.properties` ejemplo

```properties
# ============================================
# OPENAI
# ============================================
spring.ai.openai.api-key=${OPENAI_API_KEY}

# ============================================
# ANTHROPIC
# ============================================
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}

# ============================================
# DEEPSEEK
# ============================================
spring.ai.deepseek.api-key=${DEEPSEEK_API_KEY}

# ============================================
# MISTRAL AI
# ============================================
spring.ai.mistralai.api-key=${MISTRAL_AI_API_KEY}

# ============================================
# GOOGLE GEMINI
# ============================================
spring.ai.google.genai.api-key=${GOOGLE_GENAI_API_KEY}

# ============================================
# OLLAMA (local)
# ============================================
spring.ai.ollama.base-url=http://localhost:11434
```

## Selector de Provider en Código

Para usar un provider específico en los orquestadores:

```java
// En LlmClient interface tienes:
llmClient.chatAnthropic(systemPrompt, userMessage);      // Claude
llmClient.chatDeepSeek(systemPrompt, userMessage);      // DeepSeek
llmClient.chatOpenAi(systemPrompt, userMessage);        // GPT
llmClient.chatMistral(systemPrompt, userMessage);       // Mistral
llmClient.chatGemini(systemPrompt, userMessage);         // Gemini
llmClient.chatOllama(systemPrompt, userMessage);         // Ollama (default)

// O genérico:
llmClient.chatWithModel(systemPrompt, userMessage, ModelProvider.ANTHROPIC);
```

## Mapeo de Agentes a Providers

Según AGENTS.md, el mapeo recomendado es:

| Agente | Provider | Notas |
|--------|----------|-------|
| GatekeeperAgent | **Anthropic** | Mejor para preguntas de refinamiento |
| ContextEnricherAgent | **Anthropic** | Síntesis de contexto |
| ProductAgent | Cualquiera | Mantiene rol de producto |
| ArchitectAgent | Cualquiera | Razonamiento técnico |
| **CriticAgent** | **DeepSeek** | Devil's advocate - pensamiento crítico |
| ContextDocSynthesizer | **Anthropic** | Documentación técnica |
| FlowDocSynthesizer | **OpenAI** | Diagramas y flows |
| TaskDocSynthesizer | **Anthropic** | Descomposición de tareas |
| **ValidationCriticAgent** | **DeepSeek** | Auditoría de docs |
| ValidationAgent | **OpenAI** | Evaluación de convergencia |
| StackArchitectAgent | **Anthropic** | Decisiones de stack |
| ScrumMasterAgent | **Anthropic** | Estimación de tareas |

## Desarrollo

```bash
# Compilar
mvn clean compile

# Ejecutar tests
mvn test

# Ejecutar la aplicación
mvn spring-boot:run
```