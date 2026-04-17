package com.ideapipeline.model;

public record ValidationResult(
        int convergenceScore,
        boolean shouldLoop,
        String reasoning
) {}
