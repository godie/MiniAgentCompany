package com.ideapipeline.controller.dto;

public record RunPipelineResponse(
        String jobId,
        String status,
        String message
) {
}