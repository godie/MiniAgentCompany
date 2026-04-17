package com.ideapipeline.model;

import java.util.List;

public record ArchitectureDoc(
        String stack,
        List<String> services,
        List<String> constraints,
        String deploymentModel,
        String rationale
) {}
