package com.ideapipeline.model;

import java.util.List;

public record PipelineState(
        IdeaContext context,
        List<DebateMessage> debateHistory,
        PipelineOutput output,
        int loopCount
) {}
