package com.ideapipeline.client;

import com.ideapipeline.model.DebateMessage;
import java.util.List;

public interface LlmClient {
    
    /**
     * Sends a simple message to the LLM for general chat.
     * @param systemPrompt The system instruction guiding the LLM.
     * @param userMessage The content from the user.
     * @return The generated response string.
     */
    String chat(String systemPrompt, String userMessage);

    /**
     * Sends a message to the LLM while maintaining conversational history.
     * @param systemPrompt The system instruction guiding the LLM.
     * @param history The history of previous messages in the debate.
     * @param userMessage The content from the user for this turn.
     * @return The generated response string.
     */
    String chatWithHistory(String systemPrompt, List<DebateMessage> history, String userMessage);

    // Rule: Uses Spring AI ChatClient, Constructor injection only, @Slf4j, UnsupportedOperationException body.
}
