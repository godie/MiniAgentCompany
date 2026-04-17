package com.ideapipeline.model;

import java.util.List;
import java.util.Map;

public record EstimatedTask(
        Task task,
        int storyPoints,
        Map<String, Integer> votes,
        List<String> dependencies
) {}
