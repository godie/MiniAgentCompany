package com.ideapipeline.model;

public record RunRequest(
        String triggerDescription,
        RawIdea idea,
        Team team,
        int debateRounds
) {}
