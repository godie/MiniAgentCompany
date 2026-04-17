package com.ideapipeline.model;

import java.util.List;

public record CritiqueResult(
        List<String> critiques,
        List<String> gaps,
        List<String> refinementNeeds
) {}
