package com.ideapipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Main application entry point for the Idea Pipeline Orchestrator.
 * ComponentScan ensures all packages within com.ideapipeline are considered.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.ideapipeline")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}