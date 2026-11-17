package com.ideapipeline.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.ideapipeline.client.JsonParser;
import com.ideapipeline.client.LlmClient;
import com.ideapipeline.exception.PipelineException;
import com.ideapipeline.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class ScrumMasterOrchestrator {

    private static final String SCRUM_MASTER_SYSTEM_PROMPT = """
            You are a Scrum Master who knows the full technical stack and the team.
            You will simulate a Planning Poker session for all tasks provided.

            For each task:
            - Estimate story points using Fibonacci scale only: 1, 2, 3, 5, 8, 13, 21
            - Simulate a vote from each team member (use their name as key, Fibonacci value as value)
            - storyPoints is the consensus (median or most common vote)
            - Only assign dependencies that are real technical blockers

            Rules:
            - Justify estimates >= 8 with a concrete technical risk in the task description
            - votes must include every team member by name
            - adjacency maps each taskId to the list of taskIds that must be completed before it
            - A task with no dependencies maps to an empty array in adjacency
            - pointsByRole maps each BaseRole name to total points of tasks primarily owned by that role
            - All storyPoints values must be from the Fibonacci scale: 1, 2, 3, 5, 8, 13, 21

            Respond ONLY in JSON with this exact format, no preamble, no markdown:
            {
              "tasks": [
                {
                  "taskId": "task-id",
                  "storyPoints": 5,
                  "votes": {"Alice": 5, "Bob": 3}
                }
              ],
              "adjacency": {
                "task-001": ["task-002"],
                "task-002": []
              },
              "totalPoints": 42,
              "pointsByRole": {
                "BACKEND_DEV": 21,
                "FRONTEND_DEV": 13,
                "QA_ENGINEER": 8
              }
            }
            """;

    private static final Set<Integer> FIBONACCI = Set.of(1, 2, 3, 5, 8, 13, 21);

    private final LlmClient llmClient;

    public ScrumMasterOrchestrator(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public TaskGraph estimateTasks(List<Task> tasks, ArchitectureDoc architecture, Team team) {
        try {
            if (tasks == null || tasks.isEmpty()) {
                throw new PipelineException("tasks is null or empty", "Phase4", 0);
            }
            if (architecture == null) {
                throw new PipelineException("architecture is null", "Phase4", 0);
            }
            if (team == null || team.members() == null || team.members().isEmpty()) {
                throw new PipelineException("team is null or empty", "Phase4", 0);
            }

            log.info("Estimating {} tasks with {} team members", tasks.size(), team.members().size());

            String userMessage = buildUserMessage(tasks, architecture, team);
            String response = llmClient.chat(SCRUM_MASTER_SYSTEM_PROMPT, userMessage);

            if (response == null || response.isBlank()) {
                throw new PipelineException("Empty response from scrum master LLM", "Phase4", 0);
            }

            JsonNode root;
            try {
                root = JsonParser.parse(response);
            } catch (RuntimeException e) {
                log.error("Failed to parse planning poker JSON", e);
                throw new PipelineException("Failed to parse planning poker JSON", "Phase4", 0, e);
            }

            Map<String, List<String>> adjacency = parseAdjacency(root);
            Map<String, Task> taskMap = new HashMap<>();
            for (Task task : tasks) {
                taskMap.put(task.id(), task);
            }

            List<EstimatedTask> estimatedTasks = parseTasks(root, adjacency, taskMap);
            int totalPoints = root.has("totalPoints") ? root.get("totalPoints").asInt() : 0;
            Map<String, Integer> pointsByRole = parsePointsByRole(root);

            log.info("Planning poker complete: totalPoints={}, estimatedTasks={}", totalPoints, estimatedTasks.size());

            return new TaskGraph(estimatedTasks, adjacency, totalPoints, pointsByRole);
        } catch (PipelineException e) {
            log.error("Planning poker estimation failed", e);
            throw e;
        }
    }

    private String buildUserMessage(List<Task> tasks, ArchitectureDoc architecture, Team team) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== ARCHITECTURE ===\n");
        sb.append("Stack: ").append(architecture.stack()).append("\n");
        sb.append("Deployment: ").append(architecture.deploymentModel()).append("\n");
        sb.append("Services: ").append(formatList(architecture.services())).append("\n");
        sb.append("Constraints: ").append(formatList(architecture.constraints())).append("\n");
        sb.append("Rationale: ").append(architecture.rationale()).append("\n");

        sb.append("\n=== TEAM ===\n");
        sb.append(formatTeam(team));

        sb.append("\n=== TASKS ===\n");
        sb.append(formatTasks(tasks));

        return sb.toString();
    }

    private String formatList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "(none)";
        }
        return String.join(", ", items);
    }

    private String formatTeam(Team team) {
        StringBuilder sb = new StringBuilder();
        for (TeamMember member : team.members()) {
            sb.append("- ").append(member.name())
                    .append(" | ").append(member.baseRole())
                    .append(" | ").append(member.seniority())
                    .append("\n");
        }
        return sb.toString();
    }

    private String formatTasks(List<Task> tasks) {
        List<String> blocks = new ArrayList<>();
        for (Task task : tasks) {
            StringBuilder block = new StringBuilder();
            block.append("[").append(task.id()).append("] ").append(task.title()).append("\n");
            String epicId = (task.epicId() == null || task.epicId().isBlank()) ? "(none)" : task.epicId();
            String userStory = (task.userStory() == null || task.userStory().isBlank()) ? "(none)" : task.userStory();
            block.append("Epic: ").append(epicId).append(" | Story: ").append(userStory).append("\n");
            String description = (task.description() == null || task.description().isBlank()) ? "(none)" : task.description();
            block.append(description);
            blocks.add(block.toString());
        }
        return String.join("\n\n", blocks);
    }

    private Map<String, List<String>> parseAdjacency(JsonNode root) {
        JsonNode adjacencyNode = root.get("adjacency");
        if (adjacencyNode == null || !adjacencyNode.isObject()) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = adjacencyNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            List<String> deps = new ArrayList<>();
            if (entry.getValue().isArray()) {
                for (JsonNode dep : entry.getValue()) {
                    deps.add(dep.asText());
                }
            }
            adjacency.put(entry.getKey(), deps);
        }
        return adjacency;
    }

    private List<EstimatedTask> parseTasks(JsonNode root, Map<String, List<String>> adjacency, Map<String, Task> taskMap) {
        JsonNode tasksNode = root.get("tasks");
        if (tasksNode == null || !tasksNode.isArray()) {
            return Collections.emptyList();
        }

        List<EstimatedTask> estimatedTasks = new ArrayList<>();
        for (JsonNode taskNode : tasksNode) {
            String taskId = taskNode.has("taskId") ? taskNode.get("taskId").asText() : "";
            Task task = taskMap.get(taskId);
            if (task == null) {
                log.warn("Unknown taskId in LLM response: {}", taskId);
                continue;
            }

            int storyPoints = taskNode.has("storyPoints") ? taskNode.get("storyPoints").asInt() : 1;
            storyPoints = toFibonacci(storyPoints);

            Map<String, Integer> votes = parseVotes(taskNode);
            List<String> dependencies = adjacency.getOrDefault(taskId, Collections.emptyList());

            estimatedTasks.add(new EstimatedTask(task, storyPoints, votes, dependencies));
        }
        return estimatedTasks;
    }

    private Map<String, Integer> parseVotes(JsonNode taskNode) {
        JsonNode votesNode = taskNode.get("votes");
        if (votesNode == null || !votesNode.isObject()) {
            return Collections.emptyMap();
        }

        Map<String, Integer> votes = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = votesNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            votes.put(entry.getKey(), entry.getValue().asInt());
        }
        return votes;
    }

    private Map<String, Integer> parsePointsByRole(JsonNode root) {
        JsonNode pointsNode = root.get("pointsByRole");
        if (pointsNode == null || !pointsNode.isObject()) {
            return Collections.emptyMap();
        }

        Map<String, Integer> pointsByRole = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = pointsNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            pointsByRole.put(entry.getKey(), entry.getValue().asInt());
        }
        return pointsByRole;
    }

    private int toFibonacci(int value) {
        if (FIBONACCI.contains(value)) {
            return value;
        }
        if (value < 1) {
            log.warn("Invalid storyPoints {}, defaulting to 1", value);
            return 1;
        }
        if (value > 21) {
            log.warn("Invalid storyPoints {}, defaulting to 21", value);
            return 21;
        }

        int corrected = 5;
        if (value <= 2) corrected = 2;
        else if (value <= 3) corrected = 3;
        else if (value <= 5) corrected = 5;
        else if (value <= 8) corrected = 8;
        else if (value <= 13) corrected = 13;
        else corrected = 21;

        log.warn("Non-Fibonacci storyPoints {}, corrected to {}", value, corrected);
        return corrected;
    }
}