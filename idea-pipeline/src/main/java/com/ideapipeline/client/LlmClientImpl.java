package com.ideapipeline.client;

import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.DebateMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class LlmClientImpl implements LlmClient {

    private final ChatClient chatClient;

    public LlmClientImpl(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        log.info("Calling LLM via chat(systemPrompt length: {}, userMessage length: {}).", 
                systemPrompt.length(), userMessage.length());
        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userMessage)
            ));
            return chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            log.error("LLM call failed", e);
            throw new PipelineException("LLM call failed", "LlmClient", 0);
        }
    }

    @Override
    public String chatWithHistory(String systemPrompt, List<DebateMessage> history, String userMessage) {
        log.info("Calling LLM via chatWithHistory(history messages count: {}, userMessage length: {}).", 
                history.size(), userMessage.length());
        try {
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            
            for (DebateMessage message : history) {
                messages.add(new UserMessage(message.agentName() + ": " + message.content()));
            }
            
            messages.add(new UserMessage(userMessage));
            
            Prompt prompt = new Prompt(messages);
            return chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            log.error("LLM call failed", e);
            throw new PipelineException("LLM call failed", "LlmClient", 0);
        }
    }
}
