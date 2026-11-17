package com.ideapipeline.parser;

import com.ideapipeline.model.Task;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class TaskParser {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger(1);

    public static List<Task> parse(String taskDocument) {
        if (taskDocument == null || taskDocument.isBlank()) {
            return List.of();
        }

        List<Task> tasks = new ArrayList<>();
        String currentEpicId = null;
        String currentEpicName = null;
        String currentUserStory = null;

        String[] lines = taskDocument.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            if (line.startsWith("## Epic") || line.startsWith("##EPic")) {
                String[] parts = line.split(":", 2);
                if (parts.length > 1) {
                    currentEpicId = "epic-" + ID_COUNTER.getAndIncrement();
                    currentEpicName = parts[1].trim();
                }
            } else if (line.startsWith("### User Story:") || line.startsWith("### User Story :")) {
                String[] parts = line.split(":", 2);
                if (parts.length > 1) {
                    currentUserStory = parts[1].trim();
                }
            } else if (line.startsWith("#### Task:") || line.startsWith("#### Task :")) {
                String[] parts = line.split(":", 2);
                if (parts.length > 1) {
                    String title = parts[1].trim();
                    String description = "";
                    StringBuilder descBuilder = new StringBuilder();

                    for (int j = i + 1; j < lines.length; j++) {
                        String nextLine = lines[j].trim();
                        if (nextLine.startsWith("####") || nextLine.startsWith("###") || nextLine.startsWith("## ")) {
                            break;
                        }
                        if (nextLine.startsWith("- Description:") || nextLine.startsWith("- Description :")) {
                            String[] descParts = nextLine.split(":", 2);
                            if (descParts.length > 1) {
                                descBuilder.append(descParts[1].trim());
                            }
                        } else if (nextLine.startsWith("- ") && !nextLine.startsWith("- Description") && !nextLine.startsWith("- Acceptance") && !nextLine.startsWith("- Estimate")) {
                            if (descBuilder.length() > 0) {
                                descBuilder.append(" ");
                            }
                            String[] bulletParts = nextLine.split("-", 2);
                            if (bulletParts.length > 1) {
                                descBuilder.append(bulletParts[1].trim());
                            }
                        }
                    }
                    description = descBuilder.toString().isEmpty() ? title : descBuilder.toString();

                    String taskId = "task-" + String.format("%03d", ID_COUNTER.getAndIncrement());
                    tasks.add(new Task(
                            taskId,
                            title,
                            description.isEmpty() ? null : description,
                            currentEpicId,
                            currentUserStory
                    ));
                }
            } else if (line.matches("^\\[task-\\d+\\].*")) {
                String taskId = extractBracketId(line);
                String title = extractBracketTitle(line);
                String description = null;

                for (int j = i + 1; j < lines.length; j++) {
                    String nextLine = lines[j].trim();
                    if (nextLine.startsWith("[task-") || nextLine.isEmpty() && j > i + 1) {
                        break;
                    }
                    if (!nextLine.startsWith("Epic:") && !nextLine.startsWith("Story:")) {
                        if (description == null) {
                            description = nextLine;
                        } else {
                            description += " " + nextLine;
                        }
                    }
                }

                tasks.add(new Task(taskId, title, description, currentEpicId, currentUserStory));
            }
        }

        log.info("Parsed {} tasks from task document", tasks.size());
        return tasks;
    }

    private static String extractBracketId(String line) {
        int start = line.indexOf('[');
        int end = line.indexOf(']');
        if (start >= 0 && end > start) {
            return line.substring(start + 1, end);
        }
        return "task-" + ID_COUNTER.getAndIncrement();
    }

    private static String extractBracketTitle(String line) {
        int end = line.indexOf(']');
        if (end >= 0 && end + 1 < line.length()) {
            return line.substring(end + 1).trim();
        }
        return line.trim();
    }
}