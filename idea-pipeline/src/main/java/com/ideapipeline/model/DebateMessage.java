package com.ideapipeline.model;

public record DebateMessage(
        String agentName,
        String content,
        int round
) {}
