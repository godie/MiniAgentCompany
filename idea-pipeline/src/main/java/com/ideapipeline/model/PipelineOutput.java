package com.ideapipeline.model;

import java.util.List;

public record PipelineOutput(
        String contextDocument,
        String flowDocument,
        String taskDocument
) {}
