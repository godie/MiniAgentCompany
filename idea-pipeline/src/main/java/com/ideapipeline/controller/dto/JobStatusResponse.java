package com.ideapipeline.controller.dto;

import java.time.LocalDateTime;

public record JobStatusResponse(
        String jobId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String errorMessage
) {
}