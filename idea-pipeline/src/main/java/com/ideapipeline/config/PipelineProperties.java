package com.ideapipeline.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ExecutorService;

// This record maps to pipeline: property names in application.yml
@Configuration
@ConfigurationProperties(prefix = "pipeline")
@Data
@Slf4j
public class PipelineProperties {
    // This class acts as the holder for pipeline settings read from YAML
    private int convergenceThreshold;
    private int maxLoops;
    private int defaultDebateRounds;
    private int pokerConsensusMaxDistance;
}