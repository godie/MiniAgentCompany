# ADR-001: Proveedor LLM oficial del MVP

**Fecha:** 2026-05-24
**Estado:** Aceptado
**Decisor:** Revisión de código y configuración existente

---

## Contexto

El proyecto `idea-pipeline` es un sistema multi-agente que orquesta múltiples LLMs
con roles especializados. Durante la fase de diseño manual se usaron Claude, ChatGPT
y DeepSeek como modelos por rol. En la implementación real, el sistema necesita
un proveedor LLM concreto configurado en `application.yml` y `PipelineConfig.java`.

El `pom.xml` incluye ambos starters:
- `spring-ai-starter-model-openai`
- `spring-ai-starter-model-ollama`

---

## Decisión

**Ollama (qwen3.5:4b) es el proveedor LLM oficial del MVP.**

### Evidencia en el código

1. `application.yml` tiene OpenAI explícitamente deshabilitado (`enable: false`)
   y Ollama configurado como provider activo con modelo `qwen3.5:4b`.

2. `PipelineConfig.java` importa y usa `OllamaChatModel`:
   ```java
   import org.springframework.ai.ollama.OllamaChatModel;
   // ...
   public ChatClient chatClient(OllamaChatModel chatModel) {
       return ChatClient.builder(chatModel).build();
   }
   ```

3. El sistema está funcionando con Ollama — `mvn clean compile` y `mvn test` pasan.

---

## Consecuencias

### Ventajas
- **Local-first**: Ollama corre localmente, sin dependencia de APIs externas pagas.
- **Sin límites de rate**: Ideal para un pipeline que puede hacer muchas llamadas
  secuenciales y paralelas.
- **Modelo pequeño y rápido**: qwen3.5:4b es ligero (4B params), respuesta rápida.
- **Reproducible**: Cualquier developer con Ollama instalado puede ejecutar el pipeline.

### Desventajas
- **Calidad limitada**: qwen3.5:4b tiene menos capacidad de razonamiento que
  GPT-4o o Claude 3.5 Sonnet. Los outputs pueden ser menos precisos.
- **JSON a veces mal formado**: Modelos pequeños tienden a producir JSON inválido
  con más frecuencia. El `JsonParser` maneja markdown fences pero no corrige
  JSON estructuralmente roto.
- **No sigue instrucciones complejas tan bien**: Los system prompts muy largos
  o con muchas restricciones pueden ser ignorados parcialmente.

### Neutral
- Los documentos (`AGENTS.md`, `META_PIPELINE.md`) preservan las recomendaciones
  de modelo ideal por rol (Claude para arquitectura, DeepSeek para crítica, etc.)
  como guía conceptual. En producción, todos los roles usan qwen3.5:4b con
  distintos system prompts.

---

## Alternativas consideradas

### OpenAI (gpt-4o-mini)
- **A favor**: Mejor calidad de outputs, JSON más confiable, documentado en el diseño original.
- **En contra**: Requiere API key, tiene costos, límites de rate, latencia de red.
- **Decisión**: Descartado para MVP por simplicidad local. Se puede migrar
  cambiando `application.yml` y `PipelineConfig` sin tocar los orchestrators.

### OpenAI (gpt-4o)
- **A favor**: Máxima calidad.
- **En contra**: Costo elevado para un pipeline que hace docenas de llamadas.
- **Decisión**: Descartado para MVP.

---

## Migración futura

Cambiar de Ollama a OpenAI requiere solo:

1. `application.yml`: deshabilitar `ollama`, habilitar `openai`, configurar API key y modelo.
2. `PipelineConfig.java`: cambiar `OllamaChatModel` por `OpenAiChatModel`.
3. `pom.xml`: opcionalmente remover `spring-ai-starter-model-ollama`.

Los orchestrators (`GatekeeperOrchestrator`, `DebateOrchestrator`, etc.)
dependen de la interfaz `LlmClient`, no del provider concreto. No requieren cambios.

---

## Referencias

- `idea-pipeline/src/main/resources/application.yml`
- `idea-pipeline/src/main/java/com/ideapipeline/config/PipelineConfig.java`
- `idea-pipeline/pom.xml`
- `idea-pipeline/CONTEXT.md` (sección 6 — Stack técnico)
- `idea-pipeline/META_PIPELINE.md` (sección 6 — Roles en la ejecución)
