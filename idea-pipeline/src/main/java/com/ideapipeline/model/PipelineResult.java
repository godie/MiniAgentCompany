package com.ideapipeline.model;

public record PipelineResult(
        IdeaContext context,
        PipelineOutput documents,
        ArchitectureDoc architecture,
        TaskGraph taskGraph,
        int loopsRequired
) {}
