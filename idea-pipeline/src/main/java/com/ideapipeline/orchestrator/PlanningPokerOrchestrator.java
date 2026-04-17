package com.ideapipeline.orchestrator;

import com.ideapipeline.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PlanningPokerOrchestrator {

    public TaskGraph runPlanningPoker(
            String tasksMarkdown,
            Team team,
            ArchitectureDoc architecture) throws Exception {
        log.info("Running Planning Poker session with markdown context and team members.");

        List<String> taskTitles = Arrays.asList("Implement README", "Build basic API", "Containerize service");
        List<Task> tasks = taskTitles.stream()
                .map(title -> new Task("T-" + title.replaceAll("\\s+", ""), title, "Desc", "EPI-1", "US-1"))
                .toList();

        List<EstimatedTask> estimatedTasks = tasks.stream()
                .map(task -> new EstimatedTask(task, 3, Map.of("Lead", 3), List.of()))
                .toList();

        return new TaskGraph(
                estimatedTasks,
                Map.of("T-ImplementREADME", List.of("T-BuildbasicAPI")),
                6,
                Map.of("FrontendDev", 3)
        );
    }
}
