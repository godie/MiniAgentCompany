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
    private ChatClient openAiChatClient;

    @Mock
    private ChatClient anthropicChatClient;

    @Mock
    private ChatClient deepSeekChatClient;

    @Mock
    private ChatClient mistralAiChatClient;

    @Mock
    private ChatClient googleGenAiChatClient;

    @Mock
    private ChatClient ollamaChatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec chatClientRequestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private LlmClientImpl llmClient;

    @BeforeEach
    void setUp() {
        llmClient = new LlmClientImpl(
                openAiChatClient,
                anthropicChatClient,
                deepSeekChatClient,
                mistralAiChatClient,
                googleGenAiChatClient,
                ollamaChatClient);
    }

    private void stubOllamaClient(String response) {
        reset(ollamaChatClient);
        doReturn(chatClientRequestSpec).when(ollamaChatClient).prompt(any(Prompt.class));
        doReturn(callResponseSpec).when(chatClientRequestSpec).call();
        doReturn(response).when(callResponseSpec).content();
    }

    @Test
    void chat_shouldReturnContentFromLLM() {
        String systemPrompt = "You are a helpful assistant";
        String userMessage = "Hello, how are you?";
        String expectedResponse = "I'm doing great, thanks!";

        stubOllamaClient(expectedResponse);

        String result = llmClient.chat(systemPrompt, userMessage);

        assertEquals(expectedResponse, result);
        verify(ollamaChatClient).prompt(any(Prompt.class));
        verify(chatClientRequestSpec).call();
        verify(callResponseSpec).content();
    }

    @Test
    void chat_shouldThrowPipelineExceptionOnError() {
        String systemPrompt = "You are a helpful assistant";
        String userMessage = "Hello";

        doThrow(new RuntimeException("Connection failed")).when(ollamaChatClient).prompt(any(Prompt.class));

        PipelineException exception = assertThrows(PipelineException.class, () -> {
            llmClient.chat(systemPrompt, userMessage);
        });

        assertTrue(exception.getMessage().contains("LLM call failed"));
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

        stubOllamaClient(expectedResponse);

        String result = llmClient.chatWithHistory(systemPrompt, history, userMessage);

        assertEquals(expectedResponse, result);
        verify(ollamaChatClient).prompt(any(Prompt.class));
    }

    @Test
    void chatWithHistory_shouldFormatHistoryAsAgentNameColonContent() {
        String systemPrompt = "You are a debate moderator";
        List<DebateMessage> history = new ArrayList<>();
        history.add(new DebateMessage("ProductAgent", "First message", 1));
        String userMessage = "Continue";
        String expectedResponse = "Understood";

        stubOllamaClient(expectedResponse);

        llmClient.chatWithHistory(systemPrompt, history, userMessage);

        verify(ollamaChatClient).prompt(any(Prompt.class));
    }

    @Test
    void chatWithHistory_shouldHandleEmptyHistory() {
        String systemPrompt = "You are a helpful assistant";
        List<DebateMessage> history = new ArrayList<>();
        String userMessage = "Hello";
        String expectedResponse = "Hi there!";

        stubOllamaClient(expectedResponse);

        String result = llmClient.chatWithHistory(systemPrompt, history, userMessage);

        assertEquals(expectedResponse, result);
        verify(ollamaChatClient).prompt(any(Prompt.class));
    }

    @Test
    void chatWithHistory_shouldThrowPipelineExceptionOnError() {
        String systemPrompt = "You are a helpful assistant";
        List<DebateMessage> history = new ArrayList<>();
        history.add(new DebateMessage("Agent", "Message", 1));
        String userMessage = "Hello";

        doThrow(new RuntimeException("Network error")).when(ollamaChatClient).prompt(any(Prompt.class));

        PipelineException exception = assertThrows(PipelineException.class, () -> {
            llmClient.chatWithHistory(systemPrompt, history, userMessage);
        });

        assertTrue(exception.getMessage().contains("LLM call failed"));
    }

    @Test
    void chatWithModel_shouldUseCorrectProvider() {
        String systemPrompt = "Test prompt";
        String userMessage = "Test message";
        String expectedResponse = "Anthropic response";

        doReturn(chatClientRequestSpec).when(anthropicChatClient).prompt(any(Prompt.class));
        doReturn(callResponseSpec).when(chatClientRequestSpec).call();
        doReturn(expectedResponse).when(callResponseSpec).content();

        String result = llmClient.chatWithModel(systemPrompt, userMessage, ModelProvider.ANTHROPIC);

        assertEquals(expectedResponse, result);
        verify(anthropicChatClient).prompt(any(Prompt.class));
    }

    @Test
    void chatWithHistoryAndModel_shouldUseCorrectProvider() {
        String systemPrompt = "Test prompt";
        List<DebateMessage> history = new ArrayList<>();
        history.add(new DebateMessage("Agent", "Message", 1));
        String userMessage = "Test message";
        String expectedResponse = "DeepSeek response";

        doReturn(chatClientRequestSpec).when(deepSeekChatClient).prompt(any(Prompt.class));
        doReturn(callResponseSpec).when(chatClientRequestSpec).call();
        doReturn(expectedResponse).when(callResponseSpec).content();

        String result = llmClient.chatWithHistoryAndModel(systemPrompt, history, userMessage, ModelProvider.DEEPSEEK);

        assertEquals(expectedResponse, result);
        verify(deepSeekChatClient).prompt(any(Prompt.class));
    }

    @Test
    void chatAnthropic_shouldDelegateToChatWithModel() {
        String systemPrompt = "Test prompt";
        String userMessage = "Test message";
        String expectedResponse = "Claude response";

        doReturn(chatClientRequestSpec).when(anthropicChatClient).prompt(any(Prompt.class));
        doReturn(callResponseSpec).when(chatClientRequestSpec).call();
        doReturn(expectedResponse).when(callResponseSpec).content();

        String result = llmClient.chatAnthropic(systemPrompt, userMessage);

        assertEquals(expectedResponse, result);
        verify(anthropicChatClient).prompt(any(Prompt.class));
    }

    @Test
    void chatDeepSeek_shouldDelegateToChatWithModel() {
        String systemPrompt = "Test prompt";
        String userMessage = "Test message";
        String expectedResponse = "DeepSeek response";

        doReturn(chatClientRequestSpec).when(deepSeekChatClient).prompt(any(Prompt.class));
        doReturn(callResponseSpec).when(chatClientRequestSpec).call();
        doReturn(expectedResponse).when(callResponseSpec).content();

        String result = llmClient.chatDeepSeek(systemPrompt, userMessage);

        assertEquals(expectedResponse, result);
        verify(deepSeekChatClient).prompt(any(Prompt.class));
    }

    @Test
    void chatOpenAi_shouldDelegateToChatWithModel() {
        String systemPrompt = "Test prompt";
        String userMessage = "Test message";
        String expectedResponse = "GPT response";

        doReturn(chatClientRequestSpec).when(openAiChatClient).prompt(any(Prompt.class));
        doReturn(callResponseSpec).when(chatClientRequestSpec).call();
        doReturn(expectedResponse).when(callResponseSpec).content();

        String result = llmClient.chatOpenAi(systemPrompt, userMessage);

        assertEquals(expectedResponse, result);
        verify(openAiChatClient).prompt(any(Prompt.class));
    }
}