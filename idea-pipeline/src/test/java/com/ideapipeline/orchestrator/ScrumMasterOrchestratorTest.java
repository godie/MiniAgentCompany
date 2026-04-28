package com.ideapipeline.orchestrator;

import com.ideapipeline.client.LlmClient;
import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.*;
import com.ideapipeline.model.enums.BaseRole;
import com.ideapipeline.model.enums.Seniority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScrumMasterOrchestratorTest {

    @Mock
    private LlmClient llmClient;

    @InjectMocks
    private ScrumMasterOrchestrator scrumMasterOrchestrator;

    private static final ArchitectureDoc VALID_ARCHITECTURE = new ArchitectureDoc(
            "Java 21 + Spring Boot 3.3 + PostgreSQL",
            List.of("API Gateway", "Auth Service"),
            List.of("Must support 10k concurrent users"),
            "Docker + AWS ECS",
            "Chosen for team familiarity and scalability."
    );

    private static final Team VALID_TEAM = new Team(List.of(
            new TeamMember("tm-1", "Alice", BaseRole.BACKEND_DEV, Seniority.SENIOR, List.of()),
            new TeamMember("tm-2", "Bob", BaseRole.QA_ENGINEER, Seniority.MID, List.of())
    ));

    private static final List<Task> VALID_TASKS = List.of(
            new Task("task-001", "Implement JWT authentication",
                    "Implement JWT token generation and validation using Spring Security.",
                    "epic-auth", "As a user I want to log in securely"),
            new Task("task-002", "Write integration tests for auth flow",
                    null, null, null)
    );

    private static final String FULL_JSON_RESPONSE = """
            {
              "tasks": [
                {
                  "taskId": "task-001",
                  "storyPoints": 5,
                  "votes": {"Alice": 5, "Bob": 3}
                },
                {
                  "taskId": "task-002",
                  "storyPoints": 3,
                  "votes": {"Alice": 3, "Bob": 5}
                }
              ],
              "adjacency": {
                "task-001": [],
                "task-002": ["task-001"]
              },
              "totalPoints": 8,
              "pointsByRole": {
                "BACKEND_DEV": 5,
                "QA_ENGINEER": 3
              }
            }
            """;

    @Test
    void estimateTasks_shouldReturnFullyPopulatedTaskGraph() {
        when(llmClient.chat(anyString(), anyString())).thenReturn(FULL_JSON_RESPONSE);

        TaskGraph result = scrumMasterOrchestrator.estimateTasks(VALID_TASKS, VALID_ARCHITECTURE, VALID_TEAM);

        assertEquals(2, result.tasks().size());
        assertEquals(8, result.totalPoints());
        assertEquals(5, result.pointsByRole().get("BACKEND_DEV"));
        assertEquals(3, result.pointsByRole().get("QA_ENGINEER"));
        assertNotNull(result.adjacency());
    }

    @Test
    void estimateTasks_shouldMapVotesCorrectly() {
        when(llmClient.chat(anyString(), anyString())).thenReturn(FULL_JSON_RESPONSE);

        TaskGraph result = scrumMasterOrchestrator.estimateTasks(VALID_TASKS, VALID_ARCHITECTURE, VALID_TEAM);

        EstimatedTask task1 = result.tasks().stream()
                .filter(t -> t.task().id().equals("task-001"))
                .findFirst().orElseThrow();
        assertEquals(Map.of("Alice", 5, "Bob", 3), task1.votes());
    }

    @Test
    void estimateTasks_shouldPopulateDependenciesFromAdjacency() {
        when(llmClient.chat(anyString(), anyString())).thenReturn(FULL_JSON_RESPONSE);

        TaskGraph result = scrumMasterOrchestrator.estimateTasks(VALID_TASKS, VALID_ARCHITECTURE, VALID_TEAM);

        EstimatedTask task1 = result.tasks().stream()
                .filter(t -> t.task().id().equals("task-001"))
                .findFirst().orElseThrow();
        EstimatedTask task2 = result.tasks().stream()
                .filter(t -> t.task().id().equals("task-002"))
                .findFirst().orElseThrow();

        assertEquals(Collections.emptyList(), task1.dependencies());
        assertEquals(List.of("task-001"), task2.dependencies());
    }

    @Test
    void estimateTasks_shouldSkipUnknownTaskIdWithWarn() {
        String jsonWithUnknown = """
                {
                  "tasks": [
                    {
                      "taskId": "task-001",
                      "storyPoints": 5,
                      "votes": {"Alice": 5}
                    },
                    {
                      "taskId": "unknown-task",
                      "storyPoints": 8,
                      "votes": {"Bob": 8}
                    }
                  ],
                  "adjacency": {
                    "task-001": [],
                    "unknown-task": []
                  },
                  "totalPoints": 13,
                  "pointsByRole": {}
                }
                """;
        when(llmClient.chat(anyString(), anyString())).thenReturn(jsonWithUnknown);

        TaskGraph result = scrumMasterOrchestrator.estimateTasks(VALID_TASKS, VALID_ARCHITECTURE, VALID_TEAM);

        assertEquals(1, result.tasks().size());
        assertEquals("task-001", result.tasks().get(0).task().id());
    }

    @Test
    void estimateTasks_shouldUseDefaultsForMissingFields() {
        String jsonMissingFields = """
                {
                  "tasks": [
                    {
                      "taskId": "task-001"
                    }
                  ],
                  "adjacency": {
                    "task-001": []
                  },
                  "totalPoints": 0,
                  "pointsByRole": {}
                }
                """;
        when(llmClient.chat(anyString(), anyString())).thenReturn(jsonMissingFields);

        TaskGraph result = scrumMasterOrchestrator.estimateTasks(VALID_TASKS, VALID_ARCHITECTURE, VALID_TEAM);

        EstimatedTask task = result.tasks().get(0);
        assertEquals(1, task.storyPoints());
        assertEquals(Collections.emptyMap(), task.votes());
    }

    @Test
    void estimateTasks_shouldCorrectNonFibonacciStoryPoints() {
        String jsonNonFib = """
                {
                  "tasks": [
                    {
                      "taskId": "task-001",
                      "storyPoints": 4,
                      "votes": {"Alice": 4}
                    }
                  ],
                  "adjacency": {
                    "task-001": []
                  },
                  "totalPoints": 5,
                  "pointsByRole": {}
                }
                """;
        when(llmClient.chat(anyString(), anyString())).thenReturn(jsonNonFib);

        TaskGraph result = scrumMasterOrchestrator.estimateTasks(VALID_TASKS, VALID_ARCHITECTURE, VALID_TEAM);

        assertEquals(5, result.tasks().get(0).storyPoints());
    }

    @Test
    void estimateTasks_shouldCorrectStoryPointsBelowOne() {
        String jsonZero = """
                {
                  "tasks": [
                    {
                      "taskId": "task-001",
                      "storyPoints": 0,
                      "votes": {"Alice": 0}
                    }
                  ],
                  "adjacency": {
                    "task-001": []
                  },
                  "totalPoints": 1,
                  "pointsByRole": {}
                }
                """;
        when(llmClient.chat(anyString(), anyString())).thenReturn(jsonZero);

        TaskGraph result = scrumMasterOrchestrator.estimateTasks(VALID_TASKS, VALID_ARCHITECTURE, VALID_TEAM);

        assertEquals(1, result.tasks().get(0).storyPoints());
    }

    @Test
    void estimateTasks_shouldCorrectStoryPointsAboveTwentyOne() {
        String jsonThirtyFour = """
                {
                  "tasks": [
                    {
                      "taskId": "task-001",
                      "storyPoints": 34,
                      "votes": {"Alice": 34}
                    }
                  ],
                  "adjacency": {
                    "task-001": []
                  },
                  "totalPoints": 21,
                  "pointsByRole": {}
                }
                """;
        when(llmClient.chat(anyString(), anyString())).thenReturn(jsonThirtyFour);

        TaskGraph result = scrumMasterOrchestrator.estimateTasks(VALID_TASKS, VALID_ARCHITECTURE, VALID_TEAM);

        assertEquals(21, result.tasks().get(0).storyPoints());
    }

    @Test
    void estimateTasks_shouldHandleMarkdownFencedResponse() {
        String fencedResponse = "```json\n" + FULL_JSON_RESPONSE + "\n```";
        when(llmClient.chat(anyString(), anyString())).thenReturn(fencedResponse);

        TaskGraph result = scrumMasterOrchestrator.estimateTasks(VALID_TASKS, VALID_ARCHITECTURE, VALID_TEAM);

        assertEquals(2, result.tasks().size());
        assertEquals(8, result.totalPoints());
    }

    @Test
    void estimateTasks_shouldThrowPipelineException_onNullTasks() {
        PipelineException ex = assertThrows(PipelineException.class, () ->
                scrumMasterOrchestrator.estimateTasks(null, VALID_ARCHITECTURE, VALID_TEAM));
        assertEquals("Phase4", ex.getPhase());
    }

    @Test
    void estimateTasks_shouldThrowPipelineException_onEmptyTasks() {
        PipelineException ex = assertThrows(PipelineException.class, () ->
                scrumMasterOrchestrator.estimateTasks(Collections.emptyList(), VALID_ARCHITECTURE, VALID_TEAM));
        assertEquals("Phase4", ex.getPhase());
    }

    @Test
    void estimateTasks_shouldThrowPipelineException_onNullArchitecture() {
        PipelineException ex = assertThrows(PipelineException.class, () ->
                scrumMasterOrchestrator.estimateTasks(VALID_TASKS, null, VALID_TEAM));
        assertEquals("Phase4", ex.getPhase());
    }

    @Test
    void estimateTasks_shouldThrowPipelineException_onNullTeam() {
        PipelineException ex = assertThrows(PipelineException.class, () ->
                scrumMasterOrchestrator.estimateTasks(VALID_TASKS, VALID_ARCHITECTURE, null));
        assertEquals("Phase4", ex.getPhase());
    }

    @Test
    void estimateTasks_shouldThrowPipelineException_onEmptyTeamMembers() {
        Team emptyTeam = new Team(Collections.emptyList());
        PipelineException ex = assertThrows(PipelineException.class, () ->
                scrumMasterOrchestrator.estimateTasks(VALID_TASKS, VALID_ARCHITECTURE, emptyTeam));
        assertEquals("Phase4", ex.getPhase());
    }

    @Test
    void estimateTasks_shouldThrowPipelineException_onEmptyLlmResponse() {
        when(llmClient.chat(anyString(), anyString())).thenReturn("");

        PipelineException ex = assertThrows(PipelineException.class, () ->
                scrumMasterOrchestrator.estimateTasks(VALID_TASKS, VALID_ARCHITECTURE, VALID_TEAM));
        assertEquals("Phase4", ex.getPhase());
    }

    @Test
    void estimateTasks_shouldThrowPipelineException_onInvalidJson() {
        when(llmClient.chat(anyString(), anyString())).thenReturn("not json at all");

        PipelineException ex = assertThrows(PipelineException.class, () ->
                scrumMasterOrchestrator.estimateTasks(VALID_TASKS, VALID_ARCHITECTURE, VALID_TEAM));
        assertEquals("Phase4", ex.getPhase());
    }

    @Test
    void estimateTasks_shouldFormatArchitectureInUserMessage() {
        when(llmClient.chat(anyString(), anyString())).thenReturn(FULL_JSON_RESPONSE);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        scrumMasterOrchestrator.estimateTasks(VALID_TASKS, VALID_ARCHITECTURE, VALID_TEAM);

        verify(llmClient).chat(anyString(), captor.capture());
        String userMessage = captor.getValue();

        assertTrue(userMessage.contains("=== ARCHITECTURE ==="));
        assertTrue(userMessage.contains("Stack:"));
        assertTrue(userMessage.contains("Deployment:"));
    }

    @Test
    void estimateTasks_shouldFormatTeamMembersInUserMessage() {
        when(llmClient.chat(anyString(), anyString())).thenReturn(FULL_JSON_RESPONSE);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        scrumMasterOrchestrator.estimateTasks(VALID_TASKS, VALID_ARCHITECTURE, VALID_TEAM);

        verify(llmClient).chat(anyString(), captor.capture());
        String userMessage = captor.getValue();

        assertTrue(userMessage.contains("=== TEAM ==="));
        assertTrue(userMessage.contains("- Alice | BACKEND_DEV | SENIOR"));
        assertTrue(userMessage.contains("- Bob | QA_ENGINEER | MID"));
    }

    @Test
    void estimateTasks_shouldFormatTasksInUserMessage() {
        when(llmClient.chat(anyString(), anyString())).thenReturn(FULL_JSON_RESPONSE);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        scrumMasterOrchestrator.estimateTasks(VALID_TASKS, VALID_ARCHITECTURE, VALID_TEAM);

        verify(llmClient).chat(anyString(), captor.capture());
        String userMessage = captor.getValue();

        assertTrue(userMessage.contains("=== TASKS ==="));
        assertTrue(userMessage.contains("[task-001] Implement JWT authentication"));
        assertTrue(userMessage.contains("[task-002] Write integration tests for auth flow"));
    }

    @Test
    void estimateTasks_shouldRenderNoneForNullOrEmptyArchitectureLists() {
        when(llmClient.chat(anyString(), anyString())).thenReturn(FULL_JSON_RESPONSE);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        ArchitectureDoc archWithNulls = new ArchitectureDoc(
                "Java 21", null, Collections.emptyList(), "Docker", "Rationale"
        );
        scrumMasterOrchestrator.estimateTasks(VALID_TASKS, archWithNulls, VALID_TEAM);

        verify(llmClient).chat(anyString(), captor.capture());
        String userMessage = captor.getValue();

        int noneCount = countOccurrences(userMessage, "(none)");
        assertTrue(noneCount >= 2, "Expected at least 2 occurrences of (none) for null services and empty constraints");
    }

    @Test
    void estimateTasks_shouldRenderNoneForBlankTaskFields() {
        when(llmClient.chat(anyString(), anyString())).thenReturn(FULL_JSON_RESPONSE);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        List<Task> tasksWithBlanks = List.of(
                new Task("task-001", "Some task", null, null, null)
        );
        scrumMasterOrchestrator.estimateTasks(tasksWithBlanks, VALID_ARCHITECTURE, VALID_TEAM);

        verify(llmClient).chat(anyString(), captor.capture());
        String userMessage = captor.getValue();

        assertTrue(userMessage.contains("Epic: (none) | Story: (none)"));
        assertTrue(userMessage.contains("(none)"));
    }

    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}