package com.ideapipeline.model;

import java.util.List;

public record IdeaContext(
        String rawIdea,
        List<RefinementQA> refinements,
        String enrichedSummary
) {}
