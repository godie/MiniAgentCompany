package com.ideapipeline.controller.dto;

import com.ideapipeline.model.PipelineResult;

public record JobResultResponse(
        String jobId,
        String status,
        PipelineResult result
) {
}