package com.ideapipeline.client;

import com.ideapipeline.model.DebateMessage;
import java.util.List;

public interface LlmClient {
    
    String chat(String systemPrompt, String userMessage);

    String chatWithHistory(String systemPrompt, List<DebateMessage> history, String userMessage);

    String chatWithModel(String systemPrompt, String userMessage, ModelProvider provider);

    String chatWithHistoryAndModel(String systemPrompt, List<DebateMessage> history, String userMessage, ModelProvider provider);

    default String chatAnthropic(String systemPrompt, String userMessage) {
        return chatWithModel(systemPrompt, userMessage, ModelProvider.ANTHROPIC);
    }

    default String chatDeepSeek(String systemPrompt, String userMessage) {
        return chatWithModel(systemPrompt, userMessage, ModelProvider.DEEPSEEK);
    }

    default String chatOpenAi(String systemPrompt, String userMessage) {
        return chatWithModel(systemPrompt, userMessage, ModelProvider.OPENAI);
    }

    default String chatMistral(String systemPrompt, String userMessage) {
        return chatWithModel(systemPrompt, userMessage, ModelProvider.MISTRAL_AI);
    }

    default String chatGemini(String systemPrompt, String userMessage) {
        return chatWithModel(systemPrompt, userMessage, ModelProvider.GEMINI);
    }

    default String chatOllama(String systemPrompt, String userMessage) {
        return chatWithModel(systemPrompt, userMessage, ModelProvider.OLLAMA);
    }

    default String chatWithHistoryAnthropic(String systemPrompt, List<DebateMessage> history, String userMessage) {
        return chatWithHistoryAndModel(systemPrompt, history, userMessage, ModelProvider.ANTHROPIC);
    }

    default String chatWithHistoryDeepSeek(String systemPrompt, List<DebateMessage> history, String userMessage) {
        return chatWithHistoryAndModel(systemPrompt, history, userMessage, ModelProvider.DEEPSEEK);
    }
}
