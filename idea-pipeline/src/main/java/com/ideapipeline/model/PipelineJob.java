package com.ideapipeline.model;

import com.ideapipeline.model.enums.PipelineJobStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pipeline_jobs")
@Getter
@Setter
@Slf4j
public class PipelineJob {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    private PipelineJobStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(length = 2000)
    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String resultJson;

    protected PipelineJob() {
    }

    public static PipelineJob create() {
        PipelineJob job = new PipelineJob();
        job.id = UUID.randomUUID().toString();
        job.status = PipelineJobStatus.QUEUED;
        job.createdAt = LocalDateTime.now();
        job.updatedAt = LocalDateTime.now();
        return job;
    }

    public void markProcessing() {
        this.status = PipelineJobStatus.PROCESSING;
        this.updatedAt = LocalDateTime.now();
    }

    public void markDone(String resultJson) {
        this.status = PipelineJobStatus.DONE;
        this.resultJson = resultJson;
        this.updatedAt = LocalDateTime.now();
    }

    public void markError(String errorMessage) {
        this.status = PipelineJobStatus.ERROR;
        this.errorMessage = errorMessage != null && errorMessage.length() > 2000
                ? errorMessage.substring(0, 2000)
                : errorMessage;
        this.updatedAt = LocalDateTime.now();
    }
}