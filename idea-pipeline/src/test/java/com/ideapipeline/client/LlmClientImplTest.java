package com.ideapipeline.client;

import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.DebateMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmClientImplTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec chatClientRequestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private LlmClientImpl llmClient;

    @BeforeEach
    void setUp() {
        llmClient = new LlmClientImpl(chatClient);
    }

    @Test
    void chat_shouldReturnContentFromLLM() {
        String systemPrompt = "You are a helpful assistant";
        String userMessage = "Hello, how are you?";
        String expectedResponse = "I'm doing great, thanks!";

        doReturn(chatClientRequestSpec).when(chatClient).prompt(any(Prompt.class));
        doReturn(callResponseSpec).when(chatClientRequestSpec).call();
        doReturn(expectedResponse).when(callResponseSpec).content();

        String result = llmClient.chat(systemPrompt, userMessage);

        assertEquals(expectedResponse, result);
        verify(chatClient).prompt(any(Prompt.class));
        verify(chatClientRequestSpec).call();
        verify(callResponseSpec).content();
    }

    @Test
    void chat_shouldThrowPipelineExceptionOnError() {
        String systemPrompt = "You are a helpful assistant";
        String userMessage = "Hello";

        doThrow(new RuntimeException("Connection failed")).when(chatClient).prompt(any(Prompt.class));

        PipelineException exception = assertThrows(PipelineException.class, () -> {
            llmClient.chat(systemPrompt, userMessage);
        });

        assertEquals("LLM call failed", exception.getMessage());
        assertEquals("LlmClient", exception.getPhase());
    }

    @Test
    void chatWithHistory_shouldReturnContentFromLLM() {
        String systemPrompt = "You are a debate moderator";
        List<DebateMessage> history = new ArrayList<>();
        history.add(new DebateMessage("ProductAgent", "We should prioritize user experience", 1));
        history.add(new DebateMessage("ArchitectAgent", "Technical feasibility is crucial", 1));
        String userMessage = "What's your final decision?";
        String expectedResponse = "After careful consideration...";

        doReturn(chatClientRequestSpec).when(chatClient).prompt(any(Prompt.class));
        doReturn(callResponseSpec).when(chatClientRequestSpec).call();
        doReturn(expectedResponse).when(callResponseSpec).content();

        String result = llmClient.chatWithHistory(systemPrompt, history, userMessage);

        assertEquals(expectedResponse, result);
        verify(chatClient).prompt(any(Prompt.class));
    }

    @Test
    void chatWithHistory_shouldFormatHistoryAsAgentNameColonContent() {
        String systemPrompt = "You are a debate moderator";
        List<DebateMessage> history = new ArrayList<>();
        history.add(new DebateMessage("ProductAgent", "First message", 1));
        String userMessage = "Continue";
        String expectedResponse = "Understood";

        doReturn(chatClientRequestSpec).when(chatClient).prompt(any(Prompt.class));
        doReturn(callResponseSpec).when(chatClientRequestSpec).call();
        doReturn(expectedResponse).when(callResponseSpec).content();

        llmClient.chatWithHistory(systemPrompt, history, userMessage);

        verify(chatClient).prompt(any(Prompt.class));
    }

    @Test
    void chatWithHistory_shouldHandleEmptyHistory() {
        String systemPrompt = "You are a helpful assistant";
        List<DebateMessage> history = new ArrayList<>();
        String userMessage = "Hello";
        String expectedResponse = "Hi there!";

        doReturn(chatClientRequestSpec).when(chatClient).prompt(any(Prompt.class));
        doReturn(callResponseSpec).when(chatClientRequestSpec).call();
        doReturn(expectedResponse).when(callResponseSpec).content();

        String result = llmClient.chatWithHistory(systemPrompt, history, userMessage);

        assertEquals(expectedResponse, result);
        verify(chatClient).prompt(any(Prompt.class));
    }

    @Test
    void chatWithHistory_shouldThrowPipelineExceptionOnError() {
        String systemPrompt = "You are a helpful assistant";
        List<DebateMessage> history = new ArrayList<>();
        history.add(new DebateMessage("Agent", "Message", 1));
        String userMessage = "Hello";

        doThrow(new RuntimeException("Network error")).when(chatClient).prompt(any(Prompt.class));

        PipelineException exception = assertThrows(PipelineException.class, () -> {
            llmClient.chatWithHistory(systemPrompt, history, userMessage);
        });

        assertEquals("LLM call failed", exception.getMessage());
    }
}
