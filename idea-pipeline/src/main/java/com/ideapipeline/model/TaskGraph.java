package com.ideapipeline.model;

import java.util.List;
import java.util.Map;

public record TaskGraph(
        List<EstimatedTask> tasks,
        Map<String, List<String>> adjacency,
        int totalPoints,
        Map<String, Integer> pointsByRole
) {}
