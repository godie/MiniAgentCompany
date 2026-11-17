package com.ideapipeline.repository;

import com.ideapipeline.model.PipelineJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineJobRepository extends JpaRepository<PipelineJob, String> {
}