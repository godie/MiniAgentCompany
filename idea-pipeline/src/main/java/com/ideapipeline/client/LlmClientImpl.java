package com.ideapipeline.client;

import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.DebateMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class LlmClientImpl implements LlmClient {

    private final ChatClient openAiChatClient;
    private final ChatClient anthropicChatClient;
    private final ChatClient deepSeekChatClient;
    private final ChatClient mistralAiChatClient;
    private final ChatClient googleGenAiChatClient;
    private final ChatClient ollamaChatClient;

    public LlmClientImpl(
            @Qualifier("openAiChatClient") ChatClient openAiChatClient,
            @Qualifier("anthropicChatClient") ChatClient anthropicChatClient,
            @Qualifier("deepSeekChatClient") ChatClient deepSeekChatClient,
            @Qualifier("mistralAiChatClient") ChatClient mistralAiChatClient,
            @Qualifier("googleGenAiChatClient") ChatClient googleGenAiChatClient,
            @Qualifier("ollamaChatClient") ChatClient ollamaChatClient) {
        this.openAiChatClient = openAiChatClient;
        this.anthropicChatClient = anthropicChatClient;
        this.deepSeekChatClient = deepSeekChatClient;
        this.mistralAiChatClient = mistralAiChatClient;
        this.googleGenAiChatClient = googleGenAiChatClient;
        this.ollamaChatClient = ollamaChatClient;
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        return chatWithModel(systemPrompt, userMessage, ModelProvider.OLLAMA);
    }

    @Override
    public String chatWithHistory(String systemPrompt, List<DebateMessage> history, String userMessage) {
        return chatWithHistoryAndModel(systemPrompt, history, userMessage, ModelProvider.OLLAMA);
    }

    @Override
    public String chatWithModel(String systemPrompt, String userMessage, ModelProvider provider) {
        log.info("Calling LLM via chatWithModel(provider: {}, systemPrompt length: {}, userMessage length: {}).",
                provider, systemPrompt.length(), userMessage.length());
        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userMessage)
            ));
            return getChatClient(provider).prompt(prompt).call().content();
        } catch (Exception e) {
            log.error("LLM call failed for provider: {}", provider, e);
            throw new PipelineException("LLM call failed for provider: " + provider, "LlmClient", 0);
        }
    }

    @Override
    public String chatWithHistoryAndModel(String systemPrompt, List<DebateMessage> history, String userMessage, ModelProvider provider) {
        log.info("Calling LLM via chatWithHistoryAndModel(provider: {}, history messages: {}, userMessage length: {}).",
                provider, history.size(), userMessage.length());
        try {
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));

            for (DebateMessage message : history) {
                messages.add(new UserMessage(message.agentName() + ": " + message.content()));
            }

            messages.add(new UserMessage(userMessage));

            Prompt prompt = new Prompt(messages);
            return getChatClient(provider).prompt(prompt).call().content();
        } catch (Exception e) {
            log.error("LLM call failed for provider: {}", provider, e);
            throw new PipelineException("LLM call failed for provider: " + provider, "LlmClient", 0);
        }
    }

    private ChatClient getChatClient(ModelProvider provider) {
        return switch (provider) {
            case OPENAI -> openAiChatClient;
            case ANTHROPIC -> anthropicChatClient;
            case DEEPSEEK -> deepSeekChatClient;
            case MISTRAL_AI -> mistralAiChatClient;
            case GEMINI -> googleGenAiChatClient;
            case OLLAMA -> ollamaChatClient;
        };
    }
}