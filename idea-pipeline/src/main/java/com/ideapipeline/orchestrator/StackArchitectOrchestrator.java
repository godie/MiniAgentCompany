package com.ideapipeline.orchestrator;

import com.ideapipeline.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@Slf4j
public class StackArchitectOrchestrator {

    public ArchitectureDoc defineStack(IdeaContext context, PipelineOutput documents, List<DebateMessage> debateHistory) {
        log.info("Defining technology stack based on context and converged documents.");
        return new ArchitectureDoc(
                "Java 21 + Spring Boot 4.x",
                List.of("API Gateway", "Auth Service", "Core Service"),
                List.of("Must support Java 21", "Containerized deployment"),
                "Cloud-native container orchestration",
                "Modern stack with virtual threads for scalability"
        );
    }
}
